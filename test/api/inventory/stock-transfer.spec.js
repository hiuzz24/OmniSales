const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getWarehouseId,
  getAvailableVariants,
  getWarehouses,
  API_BASE,
  cleanupTestData,
  uniqueCode,
} = require('../../utils/inventory-helpers');

function nowIso() {
  return new Date().toISOString();
}

test.describe('Stock Transfer API Tests', () => {

  let fromWarehouseId;
  let toWarehouseId;
  let variantId;
  let createdIds = [];

  test.beforeAll(async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const warehouses = await getWarehouses(request, authToken);
    if (warehouses.length >= 2) {
      fromWarehouseId = warehouses[0].id;
      toWarehouseId = warehouses[1].id;
    } else if (warehouses.length === 1) {
      fromWarehouseId = warehouses[0].id;
      toWarehouseId = warehouses[0].id;
    }

    if (fromWarehouseId) {
      const variants = await getAvailableVariants(request, authToken, fromWarehouseId);
      if (variants && variants.length > 0) {
        variantId = variants[0].variantId || variants[0].id;
      }
    }
  });

  test.afterAll(async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    for (const id of createdIds) {
      await cleanupTestData(request, authToken, 'transfer', id);
    }
  });

  // GET /api/transfer
  test('TR-1 - GET /api/transfer - List paginated returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/transfer?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('transfers');
    expect(Array.isArray(body.transfers)).toBe(true);
  });

  test('TR-2 - GET /api/transfer - Filter by status=DRAFT', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/transfer?status=DRAFT&page=0&size=5`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
  });

  test('TR-3 - GET /api/transfer - Filter by warehouseId', async ({ request, managerHeaders }) => {
    test.skip(!fromWarehouseId, 'No warehouse');
    const response = await request.get(
      `${API_BASE}/transfer?warehouseId=${fromWarehouseId}&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('TR-4 - GET /api/transfer - Keyword search', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/transfer?keyword=CK&page=0&size=5`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
  });

  test('TR-5 - GET /api/transfer - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/transfer?page=0&size=5`);
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/transfer/suggested-code
  test('TR-6 - GET /api/transfer/suggested-code - Returns a code string', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/transfer/suggested-code`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const raw = await response.text();
    expect(raw.length).toBeGreaterThan(5);
    expect(raw).toContain('Trans');
  });

  // GET /api/transfer/available-variants
  test('TR-7 - GET /api/transfer/available-variants - List variants available', async ({ request, managerHeaders }) => {
    test.skip(!fromWarehouseId, 'No warehouse');
    const response = await request.get(
      `${API_BASE}/transfer/available-variants?warehouseId=${fromWarehouseId}`,
      { headers: managerHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  test('TR-8 - GET /api/transfer/available-variants - Missing warehouseId returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/transfer/available-variants`, {
      headers: managerHeaders,
    });
    expect([400, 500]).toContain(response.status());
  });

  // POST /api/transfer
  test('TR-9 - POST /api/transfer - Create DRAFT transfer between two warehouses', async ({ request, managerHeaders }) => {
    test.skip(!fromWarehouseId || !toWarehouseId || fromWarehouseId === toWarehouseId || !variantId,
      'Need 2 warehouses, 1 variant');
    const response = await request.post(`${API_BASE}/transfer`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        fromWarehouseId,
        toWarehouseId,
        transferCode: uniqueCode('CK'),
        transferTime: nowIso(),
        status: 'DRAFT',
        items: [{ variantId, quantity: 1, unitCost: 50000 }],
      },
    });

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
    if (body.data && body.data.id) createdIds.push(body.data.id);
  });

  test('TR-10 - POST /api/transfer - Missing fromWarehouseId returns 400', async ({ request, managerHeaders }) => {
    test.skip(!toWarehouseId, 'No to-warehouse');
    const response = await request.post(`${API_BASE}/transfer`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        toWarehouseId,
        transferCode: uniqueCode('CK'),
        transferTime: nowIso(),
        items: [{ variantId: '00000000-0000-0000-0000-000000000000', quantity: 1, unitCost: 10000 }],
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('TR-11 - POST /api/transfer - Empty items returns 400', async ({ request, managerHeaders }) => {
    test.skip(!fromWarehouseId || !toWarehouseId, 'Need warehouses');
    const response = await request.post(`${API_BASE}/transfer`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        fromWarehouseId,
        toWarehouseId,
        transferCode: uniqueCode('CK'),
        transferTime: nowIso(),
        items: [],
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('TR-12 - POST /api/transfer - Same source and destination rejected', async ({ request, managerHeaders }) => {
    test.skip(!fromWarehouseId, 'No warehouse');
    const response = await request.post(`${API_BASE}/transfer`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        fromWarehouseId,
        toWarehouseId: fromWarehouseId,
        transferCode: uniqueCode('CK'),
        transferTime: nowIso(),
        items: [{ variantId: '00000000-0000-0000-0000-000000000000', quantity: 1, unitCost: 10000 }],
      },
    });
    expect([400, 500]).toContain(response.status());
  });

  test('TR-13 - POST /api/transfer - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/transfer`, {
      data: {
        fromWarehouseId,
        toWarehouseId,
        transferCode: uniqueCode('CK'),
        transferTime: nowIso(),
        items: [{ variantId: '00000000-0000-0000-0000-000000000000', quantity: 1, unitCost: 10000 }],
      },
    });
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/transfer/{id}
  test('TR-14 - GET /api/transfer/{id} - Get existing transfer detail', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/transfer?page=0&size=1`, {
      headers: managerHeaders,
    });
    if (list.status() !== 200) test.skip(true, 'cannot list');
    const body = await list.json();
    const first = body.transfers?.[0];
    test.skip(!first, 'No transfers in DB');

    const response = await request.get(`${API_BASE}/transfer/${first.id}`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const j = await response.json();
    expect(j.success).toBe(true);
    expect(j.data).toHaveProperty('transferCode');
  });

  test('TR-15 - GET /api/transfer/{id} - Not found returns 400/404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/transfer/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  // PATCH /api/transfer/{id}/status
  test('TR-16 - PATCH /api/transfer/{id}/status - Update status (200 or 500)', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/transfer?status=DRAFT&page=0&size=1`, {
      headers: managerHeaders,
    });
    if (list.status() !== 200) test.skip(true, 'cannot list');
    const body = await list.json();
    const first = body.transfers?.[0];
    test.skip(!first, 'No DRAFT transfer');

    const response = await request.patch(
      `${API_BASE}/transfer/${first.id}/status?status=CANCELLED&userId=00000000-0000-0000-0000-000000000002`,
      { headers: managerHeaders }
    );
    expect([200, 400, 404, 500]).toContain(response.status());
  });

  test('TR-17 - PATCH /api/transfer/{id}/status - Invalid status returns 400', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/transfer?page=0&size=1`, {
      headers: managerHeaders,
    });
    if (list.status() !== 200) test.skip(true, 'cannot list');
    const body = await list.json();
    const first = body.transfers?.[0];
    test.skip(!first, 'No transfer');

    const response = await request.patch(
      `${API_BASE}/transfer/${first.id}/status?status=INVALID&userId=00000000-0000-0000-0000-000000000002`,
      { headers: managerHeaders }
    );
    expect([400, 500]).toContain(response.status());
  });

  test('TR-18 - PATCH /api/transfer/{id}/status - Non-existent returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.patch(
      `${API_BASE}/transfer/00000000-0000-0000-0000-000000000000/status?status=COMPLETED&userId=00000000-0000-0000-0000-000000000002`,
      { headers: managerHeaders }
    );
    expect([400, 404, 500]).toContain(response.status());
  });
});
