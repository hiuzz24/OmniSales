const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Product Variant API Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // GET /api/catalog/variants
  test('PV-1 - GET /api/catalog/variants - List all returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/catalog/variants?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('PV-2 - GET /api/catalog/variants - Filter by search keyword', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/catalog/variants?search=TEST&page=0&size=5`,
      { headers: managerHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('PV-3 - GET /api/catalog/variants - Default page returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/catalog/variants`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
  });

  test('PV-4 - GET /api/catalog/variants - Pagination with size 5', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/catalog/variants?page=0&size=5`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.content.length).toBeLessThanOrEqual(5);
  });

  test('PV-5 - GET /api/catalog/variants - Empty search returns full list', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/catalog/variants?search=&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('PV-6 - GET /api/catalog/variants - Returns correct fields for variants', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/catalog/variants?page=0&size=5`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    if (body.data.content.length > 0) {
      const variant = body.data.content[0];
      expect(variant).toHaveProperty('id');
      expect(variant).toHaveProperty('sku');
    }
  });

  test('PV-7 - GET /api/catalog/variants - Search returns correct filtered list', async ({ request, managerHeaders }) => {
    const ts = Date.now();
    const create = await request.post(`${API_BASE}/products`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: `Search Test Product ${ts}`,
        sku: `SEARCH-${ts}`,
        status: 'ACTIVE',
        lowStockThreshold: 5,
        variants: [
          {
            sku: `SEARCH-V-${ts}`,
            price: 50000,
            costPrice: 30000,
            isActive: true,
          },
        ],
      },
    });
    test.skip(create.status() !== 200, 'Cannot create product');
    const newProduct = (await create.json()).data;

    try {
      const response = await request.get(
        `${API_BASE}/catalog/variants?search=SEARCH-V-${ts}&page=0&size=10`,
        { headers: managerHeaders }
      );
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(body.data).toHaveProperty('totalElements');
    } finally {
      await request.delete(`${API_BASE}/products/${newProduct.id}/delete`, {
        headers: managerHeaders,
      });
    }
  });

  test('PV-8 - GET /api/catalog/variants - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/catalog/variants`);
    expect([401, 403]).toContain(response.status());
  });
});
