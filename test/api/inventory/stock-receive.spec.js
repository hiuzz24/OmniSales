const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getWarehouseId,
  getVariantIdFromCatalog,
  getVariantsFromCatalog,
  cleanupTestData,
  API_BASE,
} = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

function todayIso() {
  return new Date().toISOString().split('T')[0];
}

test.describe('Stock Receive API Tests', () => {

  let warehouseId;
  let supplierId = 'b0b1c2d3-0001-0000-0000-000000000002';
  let variantId;
  let createdReceipts = [];

  test.beforeAll(async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    warehouseId = await getWarehouseId(request, authToken);
    const variants = await getVariantsFromCatalog(request, authToken);
    if (variants.length > 0) {
      variantId = variants[0].id || variants[0].variantId;
    }
  });

  test.afterEach(async ({ request }) => {
    if (createdReceipts.length) {
      const authToken = await getAuthTokenCached(request);
      for (const id of createdReceipts.splice(0)) {
        await cleanupTestData(request, authToken, 'receipt', id);
      }
    }
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // GET /api/receipts
  test('R-1 - GET /api/receipts - List with pagination returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/receipts?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('R-2 - GET /api/receipts - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/receipts`);
    expect([401, 403]).toContain(response.status());
  });

  test('R-3 - GET /api/receipts - Filter by CONFIRMED status (if supported)', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/receipts?status=CONFIRMED&page=0&size=5`, {
      headers: managerHeaders,
    });

    expect([200, 400]).toContain(response.status());
  });

  // GET /api/receipts/statistics
  test('R-4 - GET /api/receipts/statistics - Returns aggregates', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/receipts/statistics`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('totalCount');
    expect(body.data).toHaveProperty('confirmedCount');
  });

  test('R-5 - GET /api/receipts/statistics - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/receipts/statistics`);
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/receipts/next-code
  test('R-6 - GET /api/receipts/next-code - Returns code', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/receipts/next-code`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(typeof body.data).toBe('string');
    expect(body.data).toMatch(/^PN-/);
  });

  // POST /api/receipts
  test('R-7 - POST /api/receipts - Create DRAFT receipt', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const response = await request.post(`${API_BASE}/receipts`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        supplierId,
        receivedAt: todayIso(),
        items: [{ variantId, quantity: 2, unitCost: 50000 }],
        isDraft: true,
      },
    });

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('id');
    expect(body.data.status).toBe('DRAFT');
    createdReceipts.push(body.data.id);
  });

  test('R-8 - POST /api/receipts - Create CONFIRMED receipt (isDraft=false)', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const response = await request.post(`${API_BASE}/receipts`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        supplierId,
        receivedAt: todayIso(),
        items: [{ variantId, quantity: 1, unitCost: 50000 }],
        isDraft: false,
      },
    });

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.status).toBe('CONFIRMED');
    createdReceipts.push(body.data.id);
  });

  test('R-9 - POST /api/receipts - Missing warehouseId returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/receipts`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        receivedAt: todayIso(),
        items: [{ variantId: '00000000-0000-0000-0000-000000000000', quantity: 1, unitCost: 10000 }],
      },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('R-10 - POST /api/receipts - Empty items returns 400', async ({ request, managerHeaders }) => {
    test.skip(!warehouseId, 'No warehouse');
    const response = await request.post(`${API_BASE}/receipts`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        supplierId,
        receivedAt: todayIso(),
        items: [],
      },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('R-11 - POST /api/receipts - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/receipts`, {
      data: {
        warehouseId: 'c0b1c2d3-0000-0000-0000-000000000001',
        receivedAt: todayIso(),
        items: [{ variantId: '00000000-0000-0000-0000-000000000000', quantity: 1, unitCost: 10000 }],
      },
    });
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/receipts/{id}
  test('R-12 - GET /api/receipts/{id} - Get existing receipt by id', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/receipts?page=0&size=1`, {
      headers: managerHeaders,
    });
    if (list.status() !== 200) test.skip(true, 'Cannot list receipts');
    const listBody = await list.json();
    const first = listBody.data?.content?.[0];
    test.skip(!first, 'No receipts in DB');

    const response = await request.get(`${API_BASE}/receipts/${first.id}`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('receiptCode');
  });

  test('R-13 - GET /api/receipts/{id} - Not found returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/receipts/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([404, 500]).toContain(response.status());
  });

  // PUT /api/receipts/{id}
  test('R-14 - PUT /api/receipts/{id} - Update DRAFT receipt', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const create = await request.post(`${API_BASE}/receipts`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        supplierId,
        receivedAt: todayIso(),
        items: [{ variantId, quantity: 1, unitCost: 50000 }],
        isDraft: true,
      },
    });
    if (create.status() !== 200 && create.status() !== 201) test.skip(true, 'Cannot create');
    const created = (await create.json()).data;
    createdReceipts.push(created.id);

    const response = await request.put(`${API_BASE}/receipts/${created.id}`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        supplierId,
        receivedAt: todayIso(),
        notes: 'Updated by test',
        items: [{ variantId, quantity: 4, unitCost: 60000 }],
        isDraft: true,
      },
    });

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('R-15 - PUT /api/receipts/{id} - Not found returns 404/500', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const response = await request.put(
      `${API_BASE}/receipts/00000000-0000-0000-0000-000000000000`,
      {
        headers: {
          ...managerHeaders,
          'Content-Type': 'application/json',
        },
        data: {
          warehouseId,
          supplierId,
          receivedAt: todayIso(),
          items: [{ variantId, quantity: 1, unitCost: 10000 }],
          isDraft: true,
        },
      }
    );
    expect([404, 500]).toContain(response.status());
  });

  // PATCH /api/receipts/{id}/complete
  test('R-16 - PATCH /api/receipts/{id}/complete - Complete DRAFT receipt', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const create = await request.post(`${API_BASE}/receipts`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        supplierId,
        receivedAt: todayIso(),
        items: [{ variantId, quantity: 1, unitCost: 50000 }],
        isDraft: true,
      },
    });
    if (create.status() !== 200 && create.status() !== 201) test.skip(true, 'Cannot create');
    const created = (await create.json()).data;
    createdReceipts.push(created.id);

    const response = await request.patch(`${API_BASE}/receipts/${created.id}/complete`, {
      headers: managerHeaders,
    });
    expect([200, 201]).toContain(response.status());
  });

  test('R-17 - PATCH /api/receipts/{id}/complete - Non-existent returns error', async ({ request, managerHeaders }) => {
    const response = await request.patch(
      `${API_BASE}/receipts/00000000-0000-0000-0000-000000000000/complete`,
      { headers: managerHeaders }
    );
    expect([400, 404, 500]).toContain(response.status());
  });
});
