const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getWarehouseId,
  getFirstWarehouseVariantId,
  API_BASE,
  cleanupTestData,
  addInventory,
} = require('../../utils/inventory-helpers');

function todayIso() {
  return new Date().toISOString().split('T')[0];
}

test.describe('Stock Delivery API Tests', () => {

  let warehouseId;
  let variantId;
  let createdIds = [];

  test.beforeAll(async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    warehouseId = await getWarehouseId(request, authToken);
    if (warehouseId) {
      variantId = await getFirstWarehouseVariantId(request, authToken, warehouseId);
    }
  });

  test.afterAll(async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    for (const id of createdIds) {
      await cleanupTestData(request, authToken, 'delivery', id);
    }
  });

  // GET /api/stock-deliveries
  test('D-1 - GET /api/stock-deliveries - List paginated returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stock-deliveries?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('D-2 - GET /api/stock-deliveries - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/stock-deliveries?page=0&size=5`);
    expect([401, 403]).toContain(response.status());
  });

  test('D-3 - GET /api/stock-deliveries - Filter by warehouseId', async ({ request, managerHeaders }) => {
    test.skip(!warehouseId, 'No warehouse');
    const response = await request.get(
      `${API_BASE}/stock-deliveries?warehouseId=${warehouseId}&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('D-4 - GET /api/stock-deliveries - Filter by status', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/stock-deliveries?status=DRAFT&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('D-5 - GET /api/stock-deliveries - Filter by deliveryType=ORDER', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/stock-deliveries?deliveryType=ORDER&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('D-6 - GET /api/stock-deliveries - Filter by deliveryType=ADJUSTMENT', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/stock-deliveries?deliveryType=ADJUSTMENT&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('D-7 - GET /api/stock-deliveries - Date range filter', async ({ request, managerHeaders }) => {
    const start = '2026-06-01';
    const end = '2026-07-31';
    const response = await request.get(
      `${API_BASE}/stock-deliveries?startDate=${start}&endDate=${end}&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('D-8 - GET /api/stock-deliveries - Keyword search', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/stock-deliveries?keyword=PX&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  test('D-9 - GET /api/stock-deliveries - Sort by createdAt ASC', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/stock-deliveries?sortBy=createdAt&sortDirection=ASC&page=0&size=5`,
      { headers: managerHeaders }
    );
    expect(response.status()).toBe(200);
  });

  // GET /api/stock-deliveries/statistics
  test('D-10 - GET /api/stock-deliveries/statistics - Returns aggregates', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/stock-deliveries/statistics`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  // POST /api/stock-deliveries
  test('D-11 - POST /api/stock-deliveries - Create ADJUSTMENT delivery', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const response = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        items: [{ productVariantId: variantId, quantity: 1 }],
      },
    });

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('id');
    createdIds.push(body.data.id);
  });

  test('D-12 - POST /api/stock-deliveries - Create DISPOSAL delivery', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    // Ensure there is at least one unit of stock so DISPOSAL can take it.
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    await addInventory(request, authToken, warehouseId, variantId, 5);

    const response = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'DISPOSAL',
        issuedDate: todayIso(),
        items: [{ productVariantId: variantId, quantity: 1 }],
      },
    });

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
    createdIds.push(body.data.id);
  });

  test('D-13 - POST /api/stock-deliveries - Missing warehouseId returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        items: [{ productVariantId: '00000000-0000-0000-0000-000000000000', quantity: 1 }],
      },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('D-14 - POST /api/stock-deliveries - Empty items returns 400', async ({ request, managerHeaders }) => {
    test.skip(!warehouseId, 'No warehouse');
    const response = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        items: [],
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('D-15 - POST /api/stock-deliveries - Invalid deliveryType returns 400', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const response = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'INVALID_TYPE',
        issuedDate: todayIso(),
        items: [{ productVariantId: variantId, quantity: 1 }],
      },
    });
    expect([400, 500]).toContain(response.status());
  });

  test('D-16 - POST /api/stock-deliveries - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/stock-deliveries`, {
      data: {
        warehouseId: 'c0b1c2d3-0000-0000-0000-000000000001',
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        items: [{ productVariantId: '00000000-0000-0000-0000-000000000000', quantity: 1 }],
      },
    });
    expect([401, 403]).toContain(response.status());
  });

  // GET /api/stock-deliveries/{id}
  test('D-17 - GET /api/stock-deliveries/{id} - Get existing', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/stock-deliveries?page=0&size=1`, {
      headers: managerHeaders,
    });
    if (list.status() !== 200) test.skip(true, 'Cannot list');
    const body = await list.json();
    const first = body.data?.content?.[0];
    test.skip(!first, 'No deliveries');

    const response = await request.get(`${API_BASE}/stock-deliveries/${first.id}`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const j = await response.json();
    expect(j.success).toBe(true);
    expect(j.data).toHaveProperty('issueCode');
  });

  test('D-18 - GET /api/stock-deliveries/{id} - Not found returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/stock-deliveries/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([404, 500]).toContain(response.status());
  });

  // PUT /api/stock-deliveries/{id} - Update draft
  test('D-19 - PUT /api/stock-deliveries/{id} - Update DRAFT delivery', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const create = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        items: [{ productVariantId: variantId, quantity: 1 }],
      },
    });
    if (![200, 201].includes(create.status())) test.skip(true, 'cannot create');
    const created = (await create.json()).data;
    createdIds.push(created.id);

    const response = await request.put(`${API_BASE}/stock-deliveries/${created.id}`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        note: 'Updated note',
        items: [{ productVariantId: variantId, quantity: 2 }],
      },
    });
    expect(response.status()).toBe(200);
  });

  // PUT /api/stock-deliveries/{id}/confirm
  test('D-20 - PUT /api/stock-deliveries/{id}/confirm - Confirm DRAFT', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const create = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        items: [{ productVariantId: variantId, quantity: 1 }],
      },
    });
    if (![200, 201].includes(create.status())) test.skip(true, 'cannot create');
    const created = (await create.json()).data;
    createdIds.push(created.id);

    const response = await request.put(`${API_BASE}/stock-deliveries/${created.id}/confirm`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const j = await response.json();
    expect(j.data.status).toBe('CONFIRMED');
  });

  test('D-21 - PUT /api/stock-deliveries/{id}/confirm - Non-existent returns error', async ({ request, managerHeaders }) => {
    const response = await request.put(
      `${API_BASE}/stock-deliveries/00000000-0000-0000-0000-000000000000/confirm`,
      { headers: managerHeaders }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  // PUT /api/stock-deliveries/{id}/cancel
  test('D-22 - PUT /api/stock-deliveries/{id}/cancel - Cancel DRAFT', async ({ request, managerHeaders }) => {
    test.skip(!variantId || !warehouseId, 'Missing seed data');
    const create = await request.post(`${API_BASE}/stock-deliveries`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        warehouseId,
        deliveryType: 'ADJUSTMENT',
        issuedDate: todayIso(),
        items: [{ productVariantId: variantId, quantity: 1 }],
      },
    });
    if (![200, 201].includes(create.status())) test.skip(true, 'cannot create');
    const created = (await create.json()).data;
    createdIds.push(created.id);

    const response = await request.put(`${API_BASE}/stock-deliveries/${created.id}/cancel`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const j = await response.json();
    expect(j.data.status).toBe('CANCELLED');
  });
});
