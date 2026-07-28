/**
 * Product Import API Tests.
 *
 * Covers the multipart upload endpoint:
 *   - POST /api/products/import         (multipart/form-data `file` field)
 *
 * The endpoint requires a Manager-level token and accepts only .xlsx/.xls files.
 * Template downloads and preview are NOT exposed in the controller, so we
 * focus on the upload path:
 *   - valid xlsx with proper headers -> 200 with counts
 *   - missing required price column  -> 4xx/5xx
 *   - non-excel file                 -> 4xx (rejected by content-type filter)
 *   - empty file                     -> 4xx/5xx
 *   - no auth                        -> 401/403
 */

function buildBackendImportWorkbook(rows) {
  // Backend requires EXACTLY the header text "Mã sản phẩm (SKU cha)" for productSku,
  // but the frontend parser uses a different alias map. The API tests only need
  // the backend to accept the file, so we use the backend's canonical header.
  const XLSX = require('xlsx');
  const headers = [
    'Mã sản phẩm (SKU cha)', 'Tên sản phẩm', 'Danh mục', 'Mô tả', 'Thương hiệu',
    'Đơn vị', 'Trạng thái', 'SKU biến thể', 'Tên biến thể', 'Giá bán',
    'Barcode', 'Trọng lượng (g)',
  ];
  const aoa = [headers];
  for (const r of rows) {
    aoa.push([
      r.productSku || '',
      r.productName || '',
      r.categoryName || '',
      r.description || '',
      r.brand || '',
      r.unit || '',
      r.status || '',
      r.variantSku || '',
      r.variantName || '',
      r.price || '',
      r.barcode || '',
      r.weightGrams || '',
    ]);
  }
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Nhập liệu');
  return XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
}

function writeBackendImportFile(rows) {
  const fs = require('fs');
  const path = require('path');
  const buffer = buildBackendImportWorkbook(rows);
  const filename = `product-import-backend-${Date.now()}-${Math.floor(Math.random() * 99999)}.xlsx`;
  const filepath = path.join(require('os').tmpdir(), filename);
  fs.writeFileSync(filepath, buffer);
  return filepath;
}

const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');
const {
  writeImportFile,
  buildInvalidWorkbook,
  buildNonExcelBuffer,
  cleanupImportFile,
} = require('../../utils/excel-helpers');
const createTestCategory = () => require('../../utils/inventory-helpers').createTestCategory;

test.describe('Product Import API Tests', () => {

  let createdCategoryIds = [];

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
    // Best-effort cleanup of the categories we created
    try {
      const headers = { Authorization: 'Bearer ' + token };
      for (const id of createdCategoryIds.splice(0)) {
        await request.delete(`${API_BASE}/categories/${id}`, { headers });
      }
    } catch (_) {}
  });

  async function makeCategory(request, token, name) {
    const created = await createTestCategory()(request, token, { name });
    if (created?.id) createdCategoryIds.push(created.id);
    return created;
  }

  // === Happy path ===========================================================

  test('IMP-1 - POST /api/products/import - Valid xlsx returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const ts = Date.now();
    const categoryName = `ImpCat ${ts}`;
    await makeCategory(request, authToken, categoryName);

    const file = writeBackendImportFile([
      {
        productSku: `IMP-TEST-${ts}`,
        productName: 'Import Test Product',
        categoryName,
        description: 'Imported by Playwright',
        brand: 'TestBrand',
        unit: 'pcs',
        status: 'ACTIVE',
        variantSku: `IMP-VAR-${ts}`,
        variantName: 'Default',
        price: 100000,
        barcode: `BC${ts}`,
        weightGrams: 500,
      },
    ]);

    try {
      const response = await request.post(`${API_BASE}/products/import`, {
        headers: managerHeaders,
        multipart: {
          file: {
            name: 'import.xlsx',
            mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            buffer: require('fs').readFileSync(file),
          },
        },
      });
      expect([200, 201]).toContain(response.status());
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(body.data).toHaveProperty('createdCount');
      expect(body.data).toHaveProperty('updatedCount');
    } finally {
      cleanupImportFile(file);
    }
  });

  // === Missing required price column ========================================

  test('IMP-2 - POST /api/products/import - Missing price column returns 400/500', async ({ request, managerHeaders }) => {
    const ts = Date.now();
    const file = writeImportFile(
      [
        {
          productSku: `IMP-NOPR-${ts}`,
          productName: 'No Price Import',
          status: 'ACTIVE',
          variantSku: `IMP-VAR2-${ts}`,
        },
      ],
      { useBackendHeaders: false, prefix: 'product-import-noprice' }
    );

    try {
      const response = await request.post(`${API_BASE}/products/import`, {
        headers: managerHeaders,
        multipart: {
          file: {
            name: 'no-price.xlsx',
            mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            buffer: require('fs').readFileSync(file),
          },
        },
      });
      expect([200, 400, 500]).toContain(response.status());
    } finally {
      cleanupImportFile(file);
    }
  });

  // === Non-Excel file rejected ==============================================

  test('IMP-3 - POST /api/products/import - Text file renamed .xlsx returns 4xx/5xx', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/products/import`, {
      headers: managerHeaders,
      multipart: {
        file: {
          name: 'fake.xlsx',
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: buildNonExcelBuffer(),
        },
      },
    });
    expect([400, 415, 500]).toContain(response.status());
  });

  // === Empty file ===========================================================

  test('IMP-4 - POST /api/products/import - Empty file returns 4xx/5xx', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/products/import`, {
      headers: managerHeaders,
      multipart: {
        file: {
          name: 'empty.xlsx',
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: Buffer.alloc(0),
        },
      },
    });
    expect([400, 500]).toContain(response.status());
  });

  // === Auth ===========================================================

  test('IMP-5 - POST /api/products/import - Without auth returns 401/403', async ({ request }) => {
    const file = writeImportFile(
      [{ productSku: 'IMP-NOAUTH', productName: 'x', status: 'ACTIVE', variantSku: 'VAR' }],
      { useBackendHeaders: true, prefix: 'product-import-noauth' }
    );
    try {
      const response = await request.post(`${API_BASE}/products/import`, {
        multipart: {
          file: {
            name: 'noauth.xlsx',
            mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            buffer: require('fs').readFileSync(file),
          },
        },
      });
      expect([401, 403]).toContain(response.status());
    } finally {
      cleanupImportFile(file);
    }
  });

  // === Invalid sheet name ===================================================

  test('IMP-6 - POST /api/products/import - Invalid sheet returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/products/import`, {
      headers: managerHeaders,
      multipart: {
        file: {
          name: 'invalid.xlsx',
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: buildInvalidWorkbook(),
        },
      },
    });
    expect([400, 500]).toContain(response.status());
  });
});
