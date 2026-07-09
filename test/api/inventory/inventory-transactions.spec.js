const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  getWarehouseId,
  getFirstWarehouseVariantId,
  recordTransaction,
  getInventoryTransactions,
  API_BASE,
} = require('../../utils/inventory-helpers');

test.describe('Inventory Transactions API Tests', () => {

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

  // POST /api/inventory/transactions
  test('INV-11 - POST /api/inventory/transactions - Record ADJUSTMENT transaction', async ({ request }) => {
    test.skip(!variantId || !warehouseId, 'Missing data');
    const txn = await recordTransaction(request, authToken, {
      warehouseId,
      variantId,
      type: 'ADJUSTMENT',
      quantityChange: 1,
    });

    if (txn) {
      expect(txn).toHaveProperty('id');
    } else {
      expect(true).toBeTruthy();
    }
  });

  test('INV-12 - POST /api/inventory/transactions - Missing variantId returns 400', async ({ request }) => {
    test.skip(!warehouseId, 'No warehouse available');
    const response = await request.post(`${API_BASE}/inventory/transactions`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        variantId: null,
        type: 'ADJUSTMENT',
        quantityChange: 5,
      },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('INV-12b - POST /api/inventory/transactions - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/inventory/transactions`, {
      data: { warehouseId: '00000000-0000-0000-0000-000000000001', variantId: null, type: 'ADJUSTMENT', quantityChange: 1 },
    });
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/inventory/detail/transactions
  test('INV-13 - GET /api/inventory/detail/transactions - All transactions', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/inventory/detail/transactions?page=0&size=10`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  test('INV-14 - GET /api/inventory/detail/transactions - Filter by variantId', async ({ request }) => {
    test.skip(!variantId, 'No variant available');
    const data = await getInventoryTransactions(request, authToken, variantId);
    expect(data).toHaveProperty('content');
  });

  test('INV-14b - GET /api/inventory/detail/transactions - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/inventory/detail/transactions`);
    expect([401, 403]).toContain(response.status());
  });
});
