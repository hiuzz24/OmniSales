const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Inventory Low Stock API Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('INV-10 - GET /api/inventory/items/low-stock - Returns low stock items', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/inventory/items/low-stock`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body.data) || Array.isArray(body)).toBe(true);
  });

  test('INV-10b - GET /api/inventory/items/low-stock - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory/items/low-stock`);
    expect([401, 403]).toContain(response.status());
  });
});
