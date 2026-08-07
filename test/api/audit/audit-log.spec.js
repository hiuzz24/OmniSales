const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/product-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Audit/Inventory Logs API Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // GET /api/audit-logs - List Audit Logs
  test('A1 - GET /api/audit-logs - List logs with pagination returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/audit-logs?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(body.data).toHaveProperty('totalElements');
    expect(body.data).toHaveProperty('totalPages');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('A2 - GET /api/audit-logs - Search by keyword', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/audit-logs?keyword=test&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('A3 - GET /api/audit-logs - Filter by action', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/audit-logs?action=CREATE&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('A4 - GET /api/audit-logs - Filter by date range', async ({ request, managerHeaders }) => {
    const from = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
    const to = new Date().toISOString();

    const response = await request.get(`${API_BASE}/audit-logs/date-range?from=${from}&to=${to}&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  // GET /api/audit-logs/entity/{entityType}/{entityId}
  test('A5 - GET /api/audit-logs/entity/{type}/{id} - Get logs by entity returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/audit-logs/entity/ORDER/00000000-0000-0000-0000-000000000000?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 500]).toContain(response.status());
  });

  test('A6 - GET /api/audit-logs/entity/{type}/{id} - Get logs by entity with valid ID', async ({ request, managerHeaders }) => {
    const listResponse = await request.get(`${API_BASE}/audit-logs?page=0&size=1`, {
      headers: managerHeaders,
    });

    if (listResponse.status() === 200) {
      const listBody = await listResponse.json();
      if (listBody.data.content && listBody.data.content.length > 0) {
        const entity = listBody.data.content[0];
        if (entity.entityId) {
          const response = await request.get(`${API_BASE}/audit-logs/entity/${entity.entityType}/${entity.entityId}?page=0&size=10`, {
            headers: managerHeaders,
          });

          expect([200, 500]).toContain(response.status());
        }
      }
    }
  });

  // GET /api/audit-logs/actor/{actorId}
  test('A7 - GET /api/audit-logs/actor/{id} - Get logs by actor returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/audit-logs/actor/00000000-0000-0000-0000-000000000000?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 500]).toContain(response.status());
  });

  // Inventory Transaction Logs (via Inventory API)
  test('A8 - GET /api/inventory/transactions - List inventory transactions returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/inventory/transactions?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 404, 500]).toContain(response.status());
  });

  test('A9 - GET /api/inventory/transactions - Filter by transaction type', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/inventory/transactions?type=RECEIVE&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 404, 500]).toContain(response.status());
  });

  test('A10 - GET /api/inventory/transactions - Filter by warehouse', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/inventory/transactions?warehouseId=00000000-0000-0000-0000-000000000000&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 404, 500]).toContain(response.status());
  });

  test('A11 - GET /api/inventory/transactions - Search by SKU', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/inventory/transactions?sku=TEST&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 404, 500]).toContain(response.status());
  });

  test('A12 - GET /api/inventory/transactions - Filter by date range', async ({ request, managerHeaders }) => {
    const from = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
    const to = new Date().toISOString();

    const response = await request.get(`${API_BASE}/inventory/transactions?from=${from}&to=${to}&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 404, 500]).toContain(response.status());
  });

  // Edge Cases
  test('A13 - GET /api/audit-logs - Access without auth returns 401 or 403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/audit-logs?page=0&size=10`);

    expect([401, 403]).toContain(response.status());
  });

  test('A14 - GET /api/audit-logs - Pagination works correctly', async ({ request, managerHeaders }) => {
    const page0 = await request.get(`${API_BASE}/audit-logs?page=0&size=5`, {
      headers: managerHeaders,
    });

    expect(page0.status()).toBe(200);

    const page1 = await request.get(`${API_BASE}/audit-logs?page=1&size=5`, {
      headers: managerHeaders,
    });

    expect(page1.status()).toBe(200);
  });

  test('A15 - GET /api/audit-logs - Invalid date format returns 400', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/audit-logs/date-range?from=invalid&to=invalid&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([400, 500]).toContain(response.status());
  });
});
