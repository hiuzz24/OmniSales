const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  getWarehouseId,
  getFirstWarehouseVariantId,
  API_BASE,
} = require('../../utils/inventory-helpers');

test.describe('Inventory Detail API Tests', () => {

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

  // GET /api/inventory/detail/{id}
  test('INV-15 - GET /api/inventory/detail/{id} - Get item detail', async ({ request }) => {
    test.skip(!variantId || !warehouseId, 'No data');
    const listing = await request.get(
      `${API_BASE}/inventory/items?warehouseId=${warehouseId}&page=0&size=1`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );
    if (listing.status() !== 200) test.skip(true, 'cannot list');

    const listBody = await listing.json();
    const firstItem = listBody.data?.content?.[0];
    test.skip(!firstItem, 'No inventory item');

    const response = await request.get(`${API_BASE}/inventory/detail/${firstItem.id}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('variantId');
  });

  test('INV-16 - GET /api/inventory/detail/{id} - Not found returns 404/500', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/inventory/detail/00000000-0000-0000-0000-000000000000`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );
    expect([404, 500]).toContain(response.status());
  });

  test('INV-16b - GET /api/inventory/detail/{id} - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory/detail/00000000-0000-0000-0000-000000000000`);
    expect([401, 403]).toContain(response.status());
  });

  // PUT /api/inventory/detail/{id}
  test('INV-17 - PUT /api/inventory/detail/{id} - Update item detail', async ({ request }) => {
    test.skip(!variantId || !warehouseId, 'No data');
    const listing = await request.get(
      `${API_BASE}/inventory/items?warehouseId=${warehouseId}&page=0&size=1`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );
    if (listing.status() !== 200) test.skip(true, 'cannot list');

    const listBody = await listing.json();
    const firstItem = listBody.data?.content?.[0];
    test.skip(!firstItem, 'No inventory item');

    const response = await request.put(`${API_BASE}/inventory/detail/${firstItem.id}`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        productVariantName: firstItem.variantName || firstItem.productName || 'Updated Variant',
        price: 100000,
        averageCost: 60000,
        quantityOnHand: 50,
        warehouseId,
      },
    });

    expect([200, 201]).toContain(response.status());
  });
});
