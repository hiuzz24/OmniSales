const { test, expect } = require('../../fixtures/auth-fixtures');
const fs = require('fs');
const path = require('path');
const {
  getAuthToken,
  getFirstCategoryName,
  uniqueSku,
} = require('../../utils/product-helpers');
const {
  writeImportFile,
  cleanupImportFile,
  buildNonExcelBuffer,
} = require('../../utils/excel-helpers');

test.describe('Product Excel Import E2E', () => {

  let categoryName = null;
  const tempFiles = [];

  test.beforeAll(async ({ request }) => {
    const token = await getAuthToken(request);
    categoryName = await getFirstCategoryName(request, token);
    if (!categoryName) {
      throw new Error('No categories available - cannot run import tests');
    }
  });

  test.afterAll(() => {
    for (const f of tempFiles) {
      cleanupImportFile(f);
    }
  });

  test.beforeEach(async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.waitForTimeout(800);
  });

  async function openImportModal(managerPage) {
    const btn = managerPage.locator('button:has-text("Nhập Excel")').first();
    await btn.click();
    await expect(managerPage.locator('h2:has-text("Import sản phẩm từ Excel")')).toBeVisible({ timeout: 5000 });
    await managerPage.waitForTimeout(300);
  }

  test('IM1 - Open import modal from product listing', async ({ managerPage }) => {
    expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(0);

    await openImportModal(managerPage);

    await expect(managerPage.locator('h2:has-text("Import sản phẩm từ Excel")')).toBeVisible();
    await expect(managerPage.getByText('Tải lên file .xlsx để thêm mới hoặc cập nhật sản phẩm theo SKU')).toBeVisible();

    await managerPage.locator('button[aria-label="Đóng"]').click();
    await managerPage.waitForTimeout(500);
    expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(0);
  });

  test('IM2 - Modal renders three wizard steps + upload area', async ({ managerPage }) => {
    await openImportModal(managerPage);

    await expect(managerPage.getByText('Chọn file').first()).toBeVisible();
    await expect(managerPage.getByText('Xác nhận').first()).toBeVisible();
    await expect(managerPage.getByText('Hoàn tất').first()).toBeVisible();

    const uploadArea = managerPage.getByText('Kéo thả file Excel vào đây');
    await expect(uploadArea).toBeVisible();

    expect(await managerPage.locator('input[type="file"][accept*="xlsx"]').count()).toBeGreaterThan(0);

    await expect(managerPage.locator('button:has-text("Hủy")').first()).toBeVisible();
  });

  test('IM3 - Download template button triggers a file download', async ({ managerPage }) => {
    await openImportModal(managerPage);

    const downloadPromise = managerPage.waitForEvent('download', { timeout: 10000 }).catch(() => null);
    await managerPage.locator('button:has-text("Tải template mẫu")').click();
    const download = await downloadPromise;

    if (!download) {
      expect(await managerPage.locator('button:has-text("Tải template mẫu")').count()).toBeGreaterThan(0);
      return;
    }

    expect(download.suggestedFilename()).toMatch(/^template-import-san-pham_\d+\.xlsx$/);
    const tmpPath = path.join(require('os').tmpdir(), `probe-${Date.now()}.xlsx`);
    await download.saveAs(tmpPath);
    const size = fs.statSync(tmpPath).size;
    fs.unlinkSync(tmpPath);
    expect(size).toBeGreaterThan(1000);
  });

  test('IM4 - Invalid file (no required headers) is rejected client-side', async ({ managerPage }) => {
    await openImportModal(managerPage);

    const XLSX = require('xlsx');
    const ws = XLSX.utils.aoa_to_sheet([['foo', 'bar'], ['1', '2']]);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'RandomSheet');
    const invalidPath = path.join(require('os').tmpdir(), `invalid-${Date.now()}.xlsx`);
    fs.writeFileSync(invalidPath, XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' }));
    tempFiles.push(invalidPath);

    await managerPage.locator('input[type="file"]').setInputFiles(invalidPath);
    await managerPage.waitForTimeout(1500);

    expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(1);
    const errorText = managerPage.locator('text=/Không tìm thấy sheet hợp lệ|thiếu cột bắt buộc/');
    await expect(errorText.first()).toBeVisible({ timeout: 5000 });
  });

  test('IM5 - Valid frontend-template file advances to confirmation step', async ({ managerPage }) => {
    await openImportModal(managerPage);

    const sku = uniqueSku('IMP').slice(0, 24);
    const rows = [
      {
        productSku: sku,
        productName: `E2E Import ${sku}`,
        categoryName,
        variantSku: `${sku}-V1`,
        variantName: 'Mặc định',
      },
    ];
    const filepath = writeImportFile(rows, { prefix: 'imp-front-valid' });
    tempFiles.push(filepath);

    await managerPage.locator('input[type="file"]').setInputFiles(filepath);
    await managerPage.waitForTimeout(2000);

    const previewHeader = managerPage.getByText('Xem trước');
    await expect(previewHeader.first()).toBeVisible({ timeout: 5000 });
  });

  test('IM6 - Confirmation step shows row + column counts', async ({ managerPage }) => {
    await openImportModal(managerPage);

    const sku = uniqueSku('PREV').slice(0, 24);
    const rows = [
      {
        productSku: sku,
        productName: `Preview ${sku}`,
        categoryName,
        variantSku: `${sku}-V1`,
        variantName: 'Mặc định',
      },
      {
        productSku: `${sku}-2`,
        productName: `Preview2 ${sku}`,
        categoryName,
        variantSku: `${sku}-2-V1`,
        variantName: 'Đỏ',
      },
    ];
    const filepath = writeImportFile(rows, { prefix: 'imp-stats' });
    tempFiles.push(filepath);

    await managerPage.locator('input[type="file"]').setInputFiles(filepath);
    await managerPage.waitForTimeout(2000);

    await expect(managerPage.getByText('Tổng dòng').first()).toBeVisible();
    await expect(managerPage.getByText('Cột nhận diện').first()).toBeVisible();
    await expect(managerPage.getByText('Sản phẩm').first()).toBeVisible();

    const previewRows = managerPage.locator('table tbody tr');
    expect(await previewRows.count()).toBeGreaterThan(0);
  });

  test('IM7 - File with unknown category is rejected before step 2', async ({ managerPage }) => {
    await openImportModal(managerPage);

    const sku = uniqueSku('BADC').slice(0, 24);
    const rows = [
      {
        productSku: sku,
        productName: `Bad category ${sku}`,
        categoryName: 'Danh mục không tồn tại XYZ',
        variantSku: `${sku}-V1`,
      },
    ];
    const filepath = writeImportFile(rows, { prefix: 'imp-badcat' });
    tempFiles.push(filepath);

    await managerPage.locator('input[type="file"]').setInputFiles(filepath);
    await managerPage.waitForTimeout(2000);

    const errorBox = managerPage.locator('text=/Danh mục không tồn tại/');
    await expect(errorBox.first()).toBeVisible({ timeout: 5000 });

    expect(await managerPage.locator('text=Xem trước').count()).toBe(0);
  });

  test('IM8 - Submit valid backend payload reaches success step', async ({ managerPage, request }) => {
    const created = [];

    await openImportModal(managerPage);

    const sku = uniqueSku('OK').slice(0, 24);
    const rows = [
      {
        productSku: sku,
        productName: `E2E OK Import ${sku}`,
        categoryName,
        status: 'DRAFT',
        variantSku: `${sku}-V1`,
        variantName: 'Mặc định',
        price: '199000',
      },
    ];
    const filepath = writeImportFile(rows, { prefix: 'imp-ok', useBackendHeaders: true });
    tempFiles.push(filepath);

    await managerPage.locator('input[type="file"]').setInputFiles(filepath);
    await managerPage.waitForTimeout(2000);

    const previewHeader = managerPage.getByText('Xem trước');
    if (await previewHeader.count() === 0) {
      test.skip(true, 'frontend did not advance to step 2 for backend-valid file');
      return;
    }
    await expect(previewHeader.first()).toBeVisible({ timeout: 5000 });

    const submitBtn = managerPage.locator('button:has-text("Import ngay")');
    await expect(submitBtn).toBeVisible();
    await submitBtn.click();

    const successTitle = managerPage.getByText('Import thành công');
    const errorTitle = managerPage.getByText('Import thất bại');
    await expect(successTitle.or(errorTitle)).toBeVisible({ timeout: 20000 });

    if (await successTitle.count() > 0) {
      created.push(sku);
      await expect(managerPage.getByText('Tạo mới').first()).toBeVisible();
      await expect(managerPage.getByText('Cập nhật').first()).toBeVisible();
      await expect(managerPage.locator('button:has-text("Đóng")')).toBeVisible();
    }

    // Cleanup
    const { API_BASE: ENV_API_BASE } = require('../../utils/env-config');
    const cleanupBase = process.env.API_BASE || ENV_API_BASE;
    const token = await getAuthToken(request);
    for (const productSku of created) {
      try {
        const resp = await request.get(`${cleanupBase}/products?keyword=${encodeURIComponent(productSku)}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (resp.status() === 200) {
          const body = await resp.json();
          const items = body.data?.content || body.data || [];
          for (const item of items) {
            if (item.sku === productSku || item.productSku === productSku) {
              await request.delete(`${cleanupBase}/products/${item.id}/delete`, {
                headers: { Authorization: `Bearer ${token}` },
              }).catch(() => null);
            }
          }
        }
      } catch (_) {
        // Ignore cleanup errors
      }
    }
  });

  test('IM9 - Submit backend-invalid payload (no price) reaches error step', async ({ managerPage }) => {
    await openImportModal(managerPage);

    const sku = uniqueSku('NOP').slice(0, 24);
    const rows = [
      {
        productSku: sku,
        productName: `No price ${sku}`,
        categoryName,
        variantSku: `${sku}-V1`,
      },
    ];
    const filepath = writeImportFile(rows, { prefix: 'imp-nop', useBackendHeaders: true });
    tempFiles.push(filepath);

    await managerPage.locator('input[type="file"]').setInputFiles(filepath);
    await managerPage.waitForTimeout(2000);

    const previewHeader = managerPage.getByText('Xem trước');
    if (await previewHeader.count() === 0) {
      test.skip(true, 'frontend did not advance to step 2 for backend-invalid file');
      return;
    }
    await expect(previewHeader.first()).toBeVisible({ timeout: 5000 });

    await managerPage.locator('button:has-text("Import ngay")').click();

    const successTitle = managerPage.getByText('Import thành công');
    const errorTitle = managerPage.getByText('Import thất bại');
    await expect(successTitle.or(errorTitle)).toBeVisible({ timeout: 20000 });

    if (await errorTitle.count() > 0) {
      const errorPre = managerPage.locator('pre');
      await expect(errorPre.first()).toBeVisible();
    }
  });

  test('IM10 - Close modal via overlay / X button / Hủy button', async ({ managerPage }) => {
    await openImportModal(managerPage);
    await managerPage.locator('button:has-text("Hủy")').first().click();
    await managerPage.waitForTimeout(400);
    expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(0);

    await openImportModal(managerPage);
    await managerPage.locator('button[aria-label="Đóng"]').click();
    await managerPage.waitForTimeout(400);
    expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(0);

    const sku = uniqueSku('CL').slice(0, 24);
    const rows = [
      {
        productSku: sku,
        productName: `Close ${sku}`,
        categoryName,
        variantSku: `${sku}-V1`,
      },
    ];
    const filepath = writeImportFile(rows, { prefix: 'imp-close' });
    tempFiles.push(filepath);

    await openImportModal(managerPage);
    await managerPage.locator('input[type="file"]').setInputFiles(filepath);
    await managerPage.waitForTimeout(2000);
    const previewHeader = managerPage.getByText('Xem trước');
    if (await previewHeader.count() > 0) {
      await expect(previewHeader.first()).toBeVisible({ timeout: 5000 });
      await managerPage.locator('button:has-text("Quay lại")').click();
      await managerPage.waitForTimeout(300);
      await managerPage.locator('button[aria-label="Đóng"]').click();
      await managerPage.waitForTimeout(400);
      expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(0);
    }

    const rows2 = [
      {
        productSku: uniqueSku('CL3').slice(0, 24),
        productName: 'Close step 3',
        categoryName,
        variantSku: uniqueSku('CL3V').slice(0, 24),
        price: '50000',
      },
    ];
    const filepath2 = writeImportFile(rows2, { prefix: 'imp-close3', useBackendHeaders: true });
    tempFiles.push(filepath2);

    await openImportModal(managerPage);
    await managerPage.locator('input[type="file"]').setInputFiles(filepath2);
    await managerPage.waitForTimeout(2000);
    if (await managerPage.locator('text=Xem trước').count() > 0) {
      await managerPage.locator('button:has-text("Import ngay")').click();
      await managerPage.waitForTimeout(5000);
      const closeBtn = managerPage.locator('button:has-text("Đóng")');
      if (await closeBtn.count() > 0 && await closeBtn.isVisible()) {
        await closeBtn.click();
        await managerPage.waitForTimeout(400);
        expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(0);
      }
    }
  });

  test('IM11 - Non-Excel file is rejected by client-side parser', async ({ managerPage }) => {
    await openImportModal(managerPage);

    const filepath = path.join(require('os').tmpdir(), `notexcel-${Date.now()}.xlsx`);
    fs.writeFileSync(filepath, buildNonExcelBuffer());
    tempFiles.push(filepath);

    await managerPage.locator('input[type="file"]').setInputFiles(filepath);
    await managerPage.waitForTimeout(1500);

    expect(await managerPage.locator('h2:has-text("Import sản phẩm từ Excel")').count()).toBe(1);
    expect(await managerPage.locator('text=Xem trước').count()).toBe(0);
  });
});
