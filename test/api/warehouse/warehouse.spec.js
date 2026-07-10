const { test, expect } = require('../../fixtures/auth-fixtures');
const {
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

test.describe('Warehouse API Tests', () => {

  test.describe('Warehouse', () => {

    test('API-W1 - GET /api/warehouses - List warehouses', async ({ request, managerHeaders }) => {
      const response = await request.get(`${API_BASE}/warehouses`, {
        headers: managerHeaders,
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(Array.isArray(body.data)).toBe(true);
    });

    test('API-W-Auth - Without auth returns 401', async ({ request }) => {
      const response = await request.get(`${API_BASE}/warehouses`);
      expect([401, 403]).toContain(response.status());
    });
  });
});
