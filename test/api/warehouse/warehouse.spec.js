const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  getWarehouseId,
  getSupplierId,
  getVariantId,
  createTestReceipt,
  createTestDelivery,
  createTestStocktake,
  createTestTransfer,
  cleanupTestData,
  uniqueCode,
  API_BASE,
} = require('../../utils/warehouse-helpers');

const API_URL = API_BASE;

test.describe('Warehouse API Tests', () => {

  let authToken;
  let warehouseId;
  let supplierId;
  let variantId;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
    warehouseId = await getWarehouseId(request, authToken);
    supplierId = await getSupplierId(request, authToken);
    if (warehouseId) {
      variantId = await getVariantId(request, authToken, warehouseId);
    }
  });

  // WAREHOUSE - API-W1

  test.describe('Warehouse', () => {

    test('API-W1 - GET /api/warehouses - List warehouses', async ({ request }) => {
      const response = await request.get(`${API_URL}/warehouses`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(Array.isArray(body.data)).toBe(true);
    });

    test('API-W-Auth - Without auth returns 401', async ({ request }) => {
      const response = await request.get(`${API_URL}/warehouses`);

      expect([401, 403]).toContain(response.status());
    });
  });
});
