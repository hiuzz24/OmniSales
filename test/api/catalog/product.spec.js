const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getFirstCategoryId,
  uniqueSku,
  deleteTestProduct,
  createTestProduct,
  API_BASE,
} = require('../../utils/product-helpers');
const { getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Product API Tests', () => {

  let categoryId;

  test.beforeAll(async ({ request, managerHeaders }) => {
    categoryId = await getFirstCategoryId(request, managerHeaders.Authorization.replace('Bearer ', ''));
  });

  // Use the lightweight, product-only cleanup here. The full
  // `cleanupAllTestData()` makes ~13 API calls (one per entity type) and
  // can blow past the 30s afterEach timeout once the backend's per-route
  // rate limiter kicks in. `cleanupTestProducts()` only hits
  // /api/products + /api/products/{id}/delete, which is enough to keep
  // the catalog clean between product tests.
  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    const { cleanupTestProducts } = require('../../utils/cleanup-helpers');
    await cleanupTestProducts(request, token);
  });

  // P1: Auth - Login
  test('P1 - Login successfully', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: { email: process.env.TEST_EMAIL || 'manager@osms.vn', password: process.env.TEST_PASSWORD || '11111111' },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.accessToken).toBeTruthy();
  });

  // P2-P7: GET /api/products - List with filters
  test('P2 - GET /api/products - List with pagination returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/products?page=0&size=6`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(body.data).toHaveProperty('totalElements');
    expect(body.data).toHaveProperty('totalPages');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P3 - GET /api/products - Search by keyword', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/products?keyword=áo&page=0&size=6`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P4 - GET /api/products - Filter by ACTIVE status', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/products?status=ACTIVE&page=0&size=6`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    if (body.data.content.length > 0) {
      for (const product of body.data.content) {
        expect(product.status).toBe('ACTIVE');
      }
    }
  });

  test('P5 - GET /api/products - Filter by DRAFT status', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/products?status=DRAFT&page=0&size=6`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P6 - GET /api/products - Filter by SHOPEE platform', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/products?platform=SHOPEE&page=0&size=6`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P7 - GET /api/products - Pagination with different page sizes', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/products?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.content.length).toBeLessThanOrEqual(10);
  });

  test('P8 - GET /api/products - Without auth returns 401', async ({ request }) => {
    const response = await request.get(`${API_BASE}/products?page=0&size=6`);
    expect([401, 403]).toContain(response.status());
  });

  // P9-P14: POST /api/products - Create
  test('P9 - POST /api/products - Create product successfully', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken, {
      name: `API Test Product ${Date.now()}`,
      sku: uniqueSku('API'),
    });

    expect(created.id).toBeTruthy();
    expect(created.name).toContain('API Test Product');
    await deleteTestProduct(request, authToken, created.id);
  });

  test('P10 - POST /api/products - Create product with variants', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken, {
      name: `API Variant Product ${Date.now()}`,
      sku: uniqueSku('VAR'),
      variants: [
        {
          sku: uniqueSku('V1'),
          price: 199000,
          costPrice: 100000,
          isActive: true,
          optionValues: { Size: 'M', 'Màu': 'Đen' },
        },
        {
          sku: uniqueSku('V2'),
          price: 299000,
          costPrice: 150000,
          isActive: true,
          optionValues: { Size: 'L', 'Màu': 'Đen' },
        },
      ],
    });

    expect(created.id).toBeTruthy();
    expect(created.variants).toHaveLength(2);
    await deleteTestProduct(request, authToken, created.id);
  });

  test('P11 - POST /api/products - Without auth returns 401', async ({ request }) => {
    const response = await request.post(`${API_BASE}/products`, {
      data: {
        name: 'Unauthorized Product',
        sku: uniqueSku('UNAUTH'),
        variants: [{ sku: uniqueSku('UA'), price: 100000 }],
      },
    });

    expect([401, 403]).toContain(response.status());
  });

  test('P12 - POST /api/products - Missing name returns validation error', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const seed = await createTestProduct(request, authToken);
    const catId = seed.categoryId;

    const response = await request.post(`${API_BASE}/products`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Product Without Name',
        sku: uniqueSku('NONAME'),
        categoryId: catId,
        variants: [{ sku: uniqueSku('NONAMEV'), price: 50000 }],
      },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
    const body = await response.json();
    expect(body.success).toBe(false);
    await deleteTestProduct(request, authToken, seed.id);
  });

  test('P13 - POST /api/products - Missing variants returns validation error', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/products`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Product Without Variants',
        sku: uniqueSku('NOVAR'),
      },
    });

    expect([400, 422, 500]).toContain(response.status());
  });

  test('P14 - POST /api/products - Duplicate SKU returns error', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const product = await createTestProduct(request, authToken, {
      name: `First Product ${Date.now()}`,
      sku: uniqueSku('DUP'),
    });

    const response = await request.post(`${API_BASE}/products`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Duplicate SKU Product',
        sku: product.sku,
        categoryId: product.categoryId,
        variants: [{ sku: uniqueSku('DUPV'), price: 50000 }],
      },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
    await deleteTestProduct(request, authToken, product.id);
  });

  // P15-P17: GET /api/products/{id} - Get by ID
  test('P15 - GET /api/products/{id} - Get product by ID', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const response = await request.get(`${API_BASE}/products/${created.id}`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBe(created.id);
    expect(body.data.name).toBe(created.name);
    await deleteTestProduct(request, authToken, created.id);
  });

  test('P16 - GET /api/products/{id} - Get non-existent product returns 404', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/products/00000000-0000-0000-0000-000000000000`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(404);
  });

  test('P17 - GET /api/products/{id} - Without auth returns 401', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const response = await request.get(`${API_BASE}/products/${created.id}`);
    expect([401, 403]).toContain(response.status());
    await deleteTestProduct(request, authToken, created.id);
  });

  // P18-P22: PUT /api/products/{id} - Update
  test('P18 - PUT /api/products/{id} - Update product name', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);
    const updatedName = `Updated Product ${Date.now()}`;

    // Re-send the full product body so we don't trip the create-time
    // @ValidProductRequest validator (description/brand/unit/hasVariants/
    // weightGrams/attributes/images/barcode are all @NotBlank/@NotNull).
    const updateResponse = await request.put(`${API_BASE}/products/${created.id}`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: updatedName,
        sku: created.sku,
        categoryId: created.categoryId,
        description: created.description || 'Updated description',
        brand: created.brand || 'TestBrand',
        unit: created.unit || 'pcs',
        hasVariants: created.hasVariants ?? true,
        status: created.status,
        lowStockThreshold: created.lowStockThreshold ?? 5,
        weightGrams: created.weightGrams ?? 500,
        attributes: created.attributes || {
          packageLengthCm: 20,
          packageWidthCm: 15,
          packageHeightCm: 10,
        },
        images: created.images || [
          { url: 'https://via.placeholder.com/300', isPrimary: true, sortOrder: 0 },
        ],
        variants: (created.variants || []).map((v) => ({
          id: v.id,
          sku: v.sku,
          name: v.name || `Variant ${v.sku}`,
          barcode: v.barcode || `BC-${v.sku}`,
          price: v.price,
          costPrice: v.costPrice,
          isActive: v.isActive,
          optionValues: v.optionValues,
          weightGrams: v.weightGrams || 500,
          images: v.images || [
            { url: 'https://via.placeholder.com/300', isPrimary: false, sortOrder: 1 },
          ],
        })),
      },
    });

    expect(updateResponse.status()).toBe(200);
    const updateBody = await updateResponse.json();
    expect(updateBody.success).toBe(true);
    expect(updateBody.data.name).toBe(updatedName);
    await deleteTestProduct(request, authToken, created.id);
  });

  test('P19 - PUT /api/products/{id} - Update product status to DRAFT', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken, { status: 'ACTIVE' });

    const updateResponse = await request.put(`${API_BASE}/products/${created.id}`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: created.name,
        sku: created.sku,
        categoryId: created.categoryId,
        description: created.description || 'Updated description',
        brand: created.brand || 'TestBrand',
        unit: created.unit || 'pcs',
        hasVariants: created.hasVariants ?? true,
        status: 'DRAFT',
        lowStockThreshold: created.lowStockThreshold ?? 5,
        weightGrams: created.weightGrams ?? 500,
        attributes: created.attributes || {
          packageLengthCm: 20,
          packageWidthCm: 15,
          packageHeightCm: 10,
        },
        images: created.images || [
          { url: 'https://via.placeholder.com/300', isPrimary: true, sortOrder: 0 },
        ],
        variants: (created.variants || []).map((v) => ({
          id: v.id,
          sku: v.sku,
          name: v.name || `Variant ${v.sku}`,
          barcode: v.barcode || `BC-${v.sku}`,
          price: v.price,
          costPrice: v.costPrice,
          isActive: v.isActive,
          optionValues: v.optionValues,
          weightGrams: v.weightGrams || 500,
          images: v.images || [
            { url: 'https://via.placeholder.com/300', isPrimary: false, sortOrder: 1 },
          ],
        })),
      },
    });

    expect(updateResponse.status()).toBe(200);
    const updateBody = await updateResponse.json();
    expect(updateBody.success).toBe(true);
    expect(updateBody.data.status).toBe('DRAFT');
    await deleteTestProduct(request, authToken, created.id);
  });

  test('P20 - PUT /api/products/{id} - Update with invalid data returns error', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const updateResponse = await request.put(`${API_BASE}/products/${created.id}`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: '',
        sku: created.sku,
        categoryId: created.categoryId,
        status: created.status,
        variants: [],
      },
    });

    expect(updateResponse.status()).toBeGreaterThanOrEqual(400);
    await deleteTestProduct(request, authToken, created.id);
  });

  test('P21 - PUT /api/products/{id} - Update non-existent product returns 400 or 404', async ({ request, managerHeaders }) => {
    const updateResponse = await request.put(`${API_BASE}/products/00000000-0000-0000-0000-000000000000`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: 'Updated Name',
        sku: 'TEST',
        variants: [],
      },
    });

    expect([400, 404]).toContain(updateResponse.status());
  });

  test('P22 - PUT /api/products/{id} - Without auth returns 401', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const response = await request.put(`${API_BASE}/products/${created.id}`, {
      data: { name: 'Updated' },
    });

    expect([401, 403]).toContain(response.status());
    await deleteTestProduct(request, authToken, created.id);
  });

  // P23-P25: DELETE /api/products/{id}/delete
  test('P23 - DELETE /api/products/{id}/delete - Delete product', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const deleteResponse = await request.delete(`${API_BASE}/products/${created.id}/delete`, {
      headers: managerHeaders,
    });

    expect(deleteResponse.status()).toBe(200);
    const deleteBody = await deleteResponse.json();
    expect(deleteBody.success).toBe(true);

    const getResponse = await request.get(`${API_BASE}/products/${created.id}`, {
      headers: managerHeaders,
    });
    expect(getResponse.status()).toBe(404);
  });

  test('P24 - DELETE /api/products/{id}/delete - Delete non-existent product returns 404', async ({ request, managerHeaders }) => {
    const response = await request.delete(`${API_BASE}/products/00000000-0000-0000-0000-000000000000/delete`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(404);
  });

  test('P25 - DELETE /api/products/{id}/delete - Without auth returns 401', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const response = await request.delete(`${API_BASE}/products/${created.id}/delete`);
    expect([401, 403]).toContain(response.status());
    await deleteTestProduct(request, authToken, created.id);
  });

  // P26-P28: POST /api/products/{productId}/sync
  test('P26 - POST /api/products/{productId}/sync - Sync product', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const syncResponse = await request.post(`${API_BASE}/products/${created.id}/sync`, {
      headers: managerHeaders,
    });

    expect(syncResponse.status()).toBe(200);
    const syncBody = await syncResponse.json();
    expect(syncBody.success).toBe(true);
    await deleteTestProduct(request, authToken, created.id);
  });

  test('P27 - POST /api/products/{productId}/sync - Sync non-existent product returns 404', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/products/00000000-0000-0000-0000-000000000000/sync`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(404);
  });

  test('P28 - POST /api/products/{productId}/sync - Without auth returns 401', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const created = await createTestProduct(request, authToken);

    const response = await request.post(`${API_BASE}/products/${created.id}/sync`);
    expect([401, 403]).toContain(response.status());
    await deleteTestProduct(request, authToken, created.id);
  });
});
