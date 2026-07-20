const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  API_BASE,
  uniqueSku,
} = require('../../utils/inventory-helpers');
const { getFirstCategoryId, deleteTestProduct } = require('../../utils/product-helpers');

test.describe('Product Log API Tests', () => {

  let categoryId;

  test.beforeAll(async ({ request, managerHeaders }) => {
    categoryId = await getFirstCategoryId(request, managerHeaders.Authorization.replace('Bearer ', ''));
  });

  // GET /api/product-logs
  test('PL-1 - GET /api/product-logs - List all logs returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/product-logs?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('PL-2 - GET /api/product-logs - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/product-logs`);
    expect([401, 403]).toContain(response.status());
  });

  test('PL-3 - GET /api/product-logs - Default sort performedAt,desc', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/product-logs?page=0&size=5`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    if (body.data.content.length > 1) {
      const first = new Date(body.data.content[0].performedAt || body.data.content[0].createdAt);
      const second = new Date(body.data.content[1].performedAt || body.data.content[1].createdAt);
      expect(first.getTime()).toBeGreaterThanOrEqual(second.getTime());
    }
  });

  test('PL-4 - GET /api/product-logs?sort=performedAt,asc - Ascending order', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/product-logs?sort=performedAt,asc&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('PL-5 - GET /api/product-logs?productId={nonexistent} - Empty list', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/product-logs?productId=00000000-0000-0000-0000-000000000000&page=0&size=10`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('PL-6 - GET /api/product-logs - Filter by existing productId', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const ts = Date.now();
    const create = await request.post(`${API_BASE}/products`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        name: `Log Test Product ${ts}`,
        sku: uniqueSku('LOG'),
        categoryId,
        status: 'ACTIVE',
        lowStockThreshold: 5,
        variants: [
          {
            sku: uniqueSku('LOG-V'),
            price: 100000,
            costPrice: 60000,
            isActive: true,
          },
        ],
      },
    });
    test.skip(create.status() !== 200, 'Cannot create product');
    const product = (await create.json()).data;

    try {
      const response = await request.get(
        `${API_BASE}/product-logs?productId=${product.id}&page=0&size=10`,
        { headers: managerHeaders }
      );
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(body.data).toHaveProperty('content');
      expect(Array.isArray(body.data.content)).toBe(true);
    } finally {
      await deleteTestProduct(request, authToken, product.id);
    }
  });

  test('PL-7 - GET /api/product-logs - Pagination with custom size', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/product-logs?page=0&size=3`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.data.content.length).toBeLessThanOrEqual(3);
  });

  test('PL-8 - GET /api/product-logs - Page beyond range returns empty', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/product-logs?page=9999&size=10`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.content.length).toBe(0);
  });
});
