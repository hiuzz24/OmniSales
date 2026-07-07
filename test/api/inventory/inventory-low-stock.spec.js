const { test, expect } = require('@playwright/test');
const { getAuthToken, API_BASE } = require('../../utils/inventory-helpers');

test.describe('Inventory Low Stock API Tests', () => {

  let authToken;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
  });

  test('INV-10 - GET /api/inventory/items/low-stock - Returns low stock items', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory/items/low-stock`, {
      headers: { Authorization: `Bearer ${authToken}` },
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
