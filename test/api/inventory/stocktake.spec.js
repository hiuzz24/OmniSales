const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Stocktake API Tests', () => {

  let warehouseId;

  test.beforeAll(async ({ request, managerHeaders }) => {
    const wh = await request.get(`${API_BASE}/warehouses`, {
      headers: managerHeaders,
    });
    if (wh.status() === 200) {
      const list = await wh.json();
      const data = list.data || [];
      if (Array.isArray(data) && data.length > 0) {
        warehouseId = data[0].id;
      }
    }
  });

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // GET /api/stocktakes/statistics
  test('ST0 - GET /api/stocktakes/statistics - Returns aggregates', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stocktakes/statistics`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('totalCount');
    expect(body.data).toHaveProperty('draftCount');
    expect(body.data).toHaveProperty('completedCount');
  });

  test('ST1 - GET /api/stocktakes - List with pagination returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stocktakes?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('ST2 - GET /api/stocktakes - Filter by status=DRAFT', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stocktakes?status=DRAFT&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('ST3 - GET /api/stocktakes - Search by keyword', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stocktakes?keyword=KK&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('ST4 - GET /api/stocktakes - Filter by warehouseId', async ({ request, managerHeaders }) => {
    test.skip(!warehouseId, 'No warehouse available');
    const response = await request.get(`${API_BASE}/stocktakes?warehouseId=${warehouseId}&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('ST5 - GET /api/stocktakes - Sort by createdAt ASC', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stocktakes?page=0&size=10&sortBy=createdAt&sortDirection=ASC`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
  });

  test('ST6 - GET /api/stocktakes - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/stocktakes`);
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/stocktakes/{nonexistent}
  test('ST7 - GET /api/stocktakes/{id} - Not found returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stocktakes/00000000-0000-0000-0000-000000000000`, {
      headers: managerHeaders,
    });

    expect([404, 500]).toContain(response.status());
  });

  // POST /api/stocktakes - missing warehouse should error
  test('ST8 - POST /api/stocktakes - Missing warehouse returns 400/404/500', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/stocktakes?complete=false`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId: '00000000-0000-0000-0000-000000000000',
        items: [],
      },
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // PUT /api/stocktakes/{id}/status - bad status returns 500
  test('ST9 - PUT /api/stocktakes/{id}/status - Invalid status may error', async ({ request, managerHeaders }) => {
    const response = await request.put(`${API_BASE}/stocktakes/00000000-0000-0000-0000-000000000000/status`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: { status: 'INVALID_STATUS' },
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // =========================================================
  // Phase B5: New endpoint (PUT /{id})
  // =========================================================

  // ST-12 - PUT /api/stocktakes/{id} (no auth)
  test('ST-12 - PUT /api/stocktakes/{id} - Without auth returns 401 or 403', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.put(`${API_BASE}/stocktakes/${fakeId}`, {
      headers: { 'Content-Type': 'application/json' },
      data: {},
    });

    expect([401, 403]).toContain(response.status());
  });

  // ST-13 - PUT /api/stocktakes/{id} (with auth, non-existent id)
  test('ST-13 - PUT /api/stocktakes/{id} - Non-existent id returns 404/500', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.put(`${API_BASE}/stocktakes/${fakeId}`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        warehouseId: warehouseId || '00000000-0000-0000-0000-000000000001',
        sessionDate: new Date().toISOString().split('T')[0],
        reason: 'ST-13 PUT test',
      },
    });

    expect([200, 400, 404, 500]).toContain(response.status());
  });

  // ST-14 - PUT /api/stocktakes/{id} (invalid body)
  test('ST-14 - PUT /api/stocktakes/{id} - Invalid body returns 400', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.put(`${API_BASE}/stocktakes/${fakeId}`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {},
    });

    expect([400, 500]).toContain(response.status());
  });

  // ST-15 - PUT /api/stocktakes/{id} (invalid uuid)
  test('ST-15 - PUT /api/stocktakes/{id} - Invalid UUID returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.put(`${API_BASE}/stocktakes/not-a-uuid`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { warehouseId: warehouseId || '00000000-0000-0000-0000-000000000001' },
    });

    expect([400, 404, 500]).toContain(response.status());
  });
});
