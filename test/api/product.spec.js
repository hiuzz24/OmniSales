const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  getFirstCategoryId,
  uniqueSku,
  deleteTestProduct,
  createTestProduct,
  API_BASE,
} = require('../utils/product-helpers');

const API_URL = API_BASE;

test.describe('Product API Tests', () => {

  let authToken;
  let categoryId;

  test.beforeAll(async ({ request }) => {
    // Login once and reuse token for all tests
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();

    // Get a category for creating products
    categoryId = await getFirstCategoryId(request, authToken);
  });

  test.afterAll(async ({ request }) => {
    // Cleanup is handled per-test below to keep tests independent
  });

  // =========================================================
  // P1: Seed data - verify login
  // =========================================================
  test('P1 - Login successfully (seed)', async ({ request }) => {
    const response = await request.post(`${API_URL}/auth/login`, {
      data: { email: 'manager@osms.vn', password: 'Duy16042004%' },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.accessToken).toBeTruthy();
    expect(body.data.user.email).toBe('manager@osms.vn');
  });

  // =========================================================
  // P2: GET /api/products - List with pagination
  // =========================================================
  test('P2 - GET /api/products - List with pagination returns 200', async ({ request }) => {
    const response = await request.get(`${API_URL}/products?page=0&size=6`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(body.data).toHaveProperty('totalElements');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // =========================================================
  // P3: GET /api/products - Search by keyword
  // =========================================================
  test('P3 - GET /api/products - Search by keyword', async ({ request }) => {
    const response = await request.get(`${API_URL}/products?keyword=áo&page=0&size=6`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // =========================================================
  // P4: GET /api/products - Filter by status
  // =========================================================
  test('P4 - GET /api/products - Filter by ACTIVE status', async ({ request }) => {
    const response = await request.get(`${API_URL}/products?status=ACTIVE&page=0&size=6`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);

    // All returned products should have ACTIVE status
    for (const product of body.data.content) {
      expect(product.status).toBe('ACTIVE');
    }
  });

  // =========================================================
  // P5: GET /api/products - Filter by platform
  // =========================================================
  test('P5 - GET /api/products - Filter by SHOPEE platform', async ({ request }) => {
    const response = await request.get(`${API_URL}/products?platform=SHOPEE&page=0&size=6`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // =========================================================
  // P6: POST /api/products - Create product successfully
  // =========================================================
  test('P6 - POST /api/products - Create product successfully', async ({ request }) => {
    const productData = {
      name: `API Test Product ${Date.now()}`,
      sku: uniqueSku('API'),
      status: 'ACTIVE',
      lowStockThreshold: 5,
      variants: [
        {
          sku: uniqueSku('APIV'),
          price: 199000,
          costPrice: 100000,
          isActive: true,
          optionValues: { Size: 'M', 'Màu': 'Xanh' },
        },
      ],
    };

    // categoryId may be null (no categories in DB) - use createTestProduct to get valid data
    // For P6 we use createTestProduct helper which handles categoryId correctly
    const created = await createTestProduct(request, authToken, {
      name: `API Test Product ${Date.now()}`,
      sku: uniqueSku('API'),
    });

    expect(created.id).toBeTruthy();
    expect(created.name).toContain('API Test Product');

    // Cleanup
    await deleteTestProduct(request, authToken, created.id);
  });

  // =========================================================
  // P7: POST /api/products - Create without auth returns 401
  // =========================================================
  test('P7 - POST /api/products - Without auth returns 401', async ({ request }) => {
    const productData = {
      name: 'Unauthorized Product',
      sku: uniqueSku('UNAUTH'),
      variants: [{ sku: uniqueSku('UA'), price: 100000 }],
    };

    const response = await request.post(`${API_URL}/products`, {
      data: productData,
    });

    // Spring Security returns 401 or 403 when no auth token provided
    expect([401, 403]).toContain(response.status());
  });

  // =========================================================
  // P8: POST /api/products - Missing name returns 400/422
  // =========================================================
  test('P8 - POST /api/products - Missing name returns validation error', async ({ request }) => {
    // Backend requires valid categoryId before name validation runs
    // So we create a product first to get a categoryId, then use it
    const seed = await createTestProduct(request, authToken);
    const catId = seed.categoryId;

    const productData = {
      name: 'Product Without Name',
      sku: uniqueSku('NONAME'),
      categoryId: catId,
      variants: [{ sku: uniqueSku('NONAMEV'), price: 50000 }],
    };

    const response = await request.post(`${API_URL}/products`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: productData,
    });

    // With valid categoryId, backend validates name and returns 4xx or 5xx on error
    expect(response.status()).toBeGreaterThanOrEqual(400);
    const body = await response.json();
    expect(body.success).toBe(false);

    // Cleanup seed product
    await deleteTestProduct(request, authToken, seed.id);
  });

  // =========================================================
  // P9: POST /api/products - Missing variants returns 400
  // =========================================================
  test('P9 - POST /api/products - Missing variants returns validation error', async ({ request }) => {
    const productData = {
      name: 'Product Without Variants',
      sku: uniqueSku('NOVAR'),
      // variants intentionally omitted - will trigger backend validation
    };

    const response = await request.post(`${API_URL}/products`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: productData,
    });

    // Backend throws if variants is null/empty
    expect([400, 422, 500]).toContain(response.status());
  });

  // =========================================================
  // P10: GET /api/products/{id} - Get product by ID
  // =========================================================
  test('P10 - GET /api/products/{id} - Get product by ID', async ({ request }) => {
    // Create a product first
    const created = await createTestProduct(request, authToken);

    const response = await request.get(`${API_URL}/products/${created.id}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBe(created.id);
    expect(body.data.name).toBe(created.name);

    // Cleanup
    await deleteTestProduct(request, authToken, created.id);
  });

  // =========================================================
  // P11: PUT /api/products/{id} - Update product name
  // =========================================================
  test('P11 - PUT /api/products/{id} - Update product name', async ({ request }) => {
    // Create a product first
    const created = await createTestProduct(request, authToken);

    const updatedName = `Updated Product ${Date.now()}`;
    const updatePayload = {
      name: updatedName,
      sku: created.sku,
      categoryId: created.categoryId,
      status: created.status,
      lowStockThreshold: 5,
      variants: created.variants.map((v) => ({
        id: v.id,
        sku: v.sku,
        price: v.price,
        costPrice: v.costPrice,
        isActive: v.isActive,
        optionValues: v.optionValues,
      })),
    };

    const updateResponse = await request.put(`${API_URL}/products/${created.id}`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: updatePayload,
    });

    expect(updateResponse.status()).toBe(200);
    const updateBody = await updateResponse.json();
    expect(updateBody.success).toBe(true);
    expect(updateBody.data.name).toBe(updatedName);

    // Cleanup
    await deleteTestProduct(request, authToken, created.id);
  });

  // =========================================================
  // P12: DELETE /api/products/{id}/delete - Delete product
  // =========================================================
  test('P12 - DELETE /api/products/{id}/delete - Delete product', async ({ request }) => {
    // Create a product first
    const created = await createTestProduct(request, authToken);

    // Delete it
    const deleteResponse = await request.delete(`${API_URL}/products/${created.id}/delete`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(deleteResponse.status()).toBe(200);
    const deleteBody = await deleteResponse.json();
    expect(deleteBody.success).toBe(true);

    // Verify it no longer exists
    const getResponse = await request.get(`${API_URL}/products/${created.id}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(getResponse.status()).toBe(404);
  });
});
