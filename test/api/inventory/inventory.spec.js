const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  getWarehouseId,
  getFirstWarehouseVariantId,
  API_BASE,
} = require('../../utils/inventory-helpers');

test.describe('Inventory API Tests', () => {

  let authToken;
  let warehouseId;
  let variantId;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
    warehouseId = await getWarehouseId(request, authToken);
    if (warehouseId) {
      variantId = await getFirstWarehouseVariantId(request, authToken, warehouseId);
    }
  });

  // GET /api/inventory
  test('INV-1 - GET /api/inventory - List paginated returns 200', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory?page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('INV-2 - GET /api/inventory - Filter by localOnly', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory?localOnly=true&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('INV-3 - GET /api/inventory - Sort by updatedAt ASC', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory?sortBy=updatedAt&sortDir=ASC&page=0&size=5`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
  });

  test('INV-4 - GET /api/inventory - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory?page=0&size=5`);
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/inventory/category/{id}
  test('INV-5 - GET /api/inventory/category/{id} - returns paginated list', async ({ request }) => {
    test.skip(!warehouseId, 'No warehouse available');
    const catResponse = await request.get(`${API_BASE}/categories`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    const categories = catResponse.status() === 200 ? (await catResponse.json()).data || [] : [];
    test.skip(categories.length === 0, 'No categories available');

    const response = await request.get(
      `${API_BASE}/inventory/category/${categories[0].id}?page=0&size=5`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // GET /api/inventory/items?warehouseId=
  test('INV-6 - GET /api/inventory/items?warehouseId= - Returns items list', async ({ request }) => {
    test.skip(!warehouseId, 'No warehouse available');
    const response = await request.get(
      `${API_BASE}/inventory/items?warehouseId=${warehouseId}&page=0&size=5`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // GET /api/inventory/warehouses/{id}/items
  test('INV-7 - GET /api/inventory/warehouses/{id}/items - Returns warehouse items', async ({ request }) => {
    test.skip(!warehouseId, 'No warehouse available');
    const response = await request.get(
      `${API_BASE}/inventory/warehouses/${warehouseId}/items?page=0&size=5`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  // GET /api/inventory/warehouses/{id}/available-variants
  test('INV-8 - GET /api/inventory/warehouses/{id}/available-variants', async ({ request }) => {
    test.skip(!warehouseId, 'No warehouse available');
    const response = await request.get(
      `${API_BASE}/inventory/warehouses/${warehouseId}/available-variants`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // GET /api/inventory/warehouses/{id}/variants/{variantId}
  test('INV-9 - GET /api/inventory/warehouses/{id}/variants/{variantId}', async ({ request }) => {
    test.skip(!variantId, 'No variant in warehouse');
    const response = await request.get(
      `${API_BASE}/inventory/warehouses/${warehouseId}/variants/${variantId}`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('variantId');
  });
});
