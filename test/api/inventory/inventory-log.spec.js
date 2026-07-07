const { test, expect } = require('@playwright/test');
const { getAuthToken, getWarehouseId, API_BASE } = require('../../utils/inventory-helpers');

test.describe('Inventory Log API Tests', () => {

  let authToken;
  let warehouseId;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
    warehouseId = await getWarehouseId(request, authToken);
  });

  // GET /api/inventory/log
  test('INV-18 - GET /api/inventory/log - List inventory logs', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory/log?page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
  });

  test('INV-19 - GET /api/inventory/log - Filter by warehouseId', async ({ request }) => {
    test.skip(!warehouseId, 'No warehouse');
    const response = await request.get(
      `${API_BASE}/inventory/log?warehouseId=${warehouseId}&page=0&size=5`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );
    expect(response.status()).toBe(200);
  });

  test('INV-20 - GET /api/inventory/log - Filter by productSearch', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/inventory/log?productSearch=Test&page=0&size=5`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );
    expect(response.status()).toBe(200);
  });

  test('INV-21 - GET /api/inventory/log - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory/log`);
    expect([401, 403]).toContain(response.status());
  });
});
