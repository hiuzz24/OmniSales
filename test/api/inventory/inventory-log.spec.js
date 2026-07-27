const { test, expect } = require('../../fixtures/auth-fixtures');
const { getWarehouseId, API_BASE } = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Inventory Log API Tests', () => {

  let warehouseId;

  test.beforeAll(async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    warehouseId = await getWarehouseId(request, authToken);
  });

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // GET /api/inventory/log
  test('INV-18 - GET /api/inventory/log - List inventory logs', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/inventory/log?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
  });

  test('INV-19 - GET /api/inventory/log - Filter by warehouseId', async ({ request, managerHeaders }) => {
    test.skip(!warehouseId, 'No warehouse');
    const response = await request.get(
      `${API_BASE}/inventory/log?warehouseId=${warehouseId}&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('INV-20 - GET /api/inventory/log - Filter by productSearch', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/inventory/log?productSearch=Test&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('INV-21 - GET /api/inventory/log - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory/log`);
    expect([401, 403]).toContain(response.status());
  });
});
