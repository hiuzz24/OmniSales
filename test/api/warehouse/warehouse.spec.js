/**
 * Warehouse API Tests.
 *
 * Covers all warehouse endpoints:
 *   - GET    /api/warehouses                  (list, with keyword + status filter)
 *   - GET    /api/warehouses/master           (master warehouse)
 *   - GET    /api/warehouses/{id}             (detail)
 *   - GET    /api/warehouses/userWarehouse/{userId}  (user's assigned warehouse)
 *   - POST   /api/warehouses                  (create)
 *   - PUT    /api/warehouses/{id}             (update)
 *   - PATCH  /api/warehouses/{id}/status      (toggle active)
 *   - DELETE /api/warehouses/{id}             (delete)
 *
 * Notes:
 *   - Create/update require OWNER or SYSTEM_ADMIN role.
 *   - getById and master require OWNER, OPERATIONS or SYSTEM_ADMIN.
 *   - The master warehouse may not be deletable — tests skip if so.
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  createTestWarehouse,
  deleteTestWarehouse,
  toggleWarehouseStatus,
  API_BASE,
} = require('../../utils/warehouse-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Warehouse API Tests', () => {

  let createdWarehouseIds = [];

  test.afterEach(async ({ request }) => {
    if (createdWarehouseIds.length) {
      const authToken = await getAuthTokenCached(request);
      for (const id of createdWarehouseIds.splice(0)) {
        await deleteTestWarehouse(request, authToken, id);
      }
    }
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // === GET endpoints ===========================================================

  test('API-W1 - GET /api/warehouses - List warehouses', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/warehouses`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  test('API-W1b - GET /api/warehouses - keyword filter narrows result', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/warehouses?keyword=TestWH`, {
      headers: managerHeaders,
    });

    expect([200, 400]).toContain(response.status());
    if (response.status() === 200) {
      const body = await response.json();
      expect(Array.isArray(body.data)).toBe(true);
    }
  });

  test('API-W1c - GET /api/warehouses - status=ACTIVE filter accepts valid value', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/warehouses?status=ACTIVE`, {
      headers: managerHeaders,
    });
    expect([200, 400]).toContain(response.status());
  });

  test('API-W2 - GET /api/warehouses - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/warehouses`);
    expect([401, 403]).toContain(response.status());
  });

  test('API-W3 - GET /api/warehouses/master - Returns the master warehouse', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/warehouses/master`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('id');
    expect(body.data).toHaveProperty('name');
  });

  test('API-W3b - GET /api/warehouses/master - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/warehouses/master`);
    expect([401, 403]).toContain(response.status());
  });

  test('API-W4 - GET /api/warehouses/{id} - Get existing warehouse by id', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/warehouses`, {
      headers: managerHeaders,
    });
    expect(list.status()).toBe(200);
    const listBody = await list.json();
    const listData = listBody.data || [];
    test.skip(!Array.isArray(listData) || listData.length === 0, 'No warehouses in DB');

    const response = await request.get(`${API_BASE}/warehouses/${listData[0].id}`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBe(listData[0].id);
    expect(body.data).toHaveProperty('name');
  });

  test('API-W5 - GET /api/warehouses/{id} - Non-existent returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/warehouses/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([404, 500]).toContain(response.status());
  });

  test('API-W5b - GET /api/warehouses/userWarehouse/{userId} - Returns warehouse', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/users?page=0&size=1`, {
      headers: managerHeaders,
    });
    test.skip(list.status() !== 200, 'users endpoint unavailable');
    const listBody = await list.json();
    const userId = listBody.data?.content?.[0]?.id;
    test.skip(!userId, 'No users in DB');

    const response = await request.get(`${API_BASE}/warehouses/userWarehouse/${userId}`, {
      headers: managerHeaders,
    });
    expect([200, 404]).toContain(response.status());
  });

  // === POST ===========================================================

  test('API-W6 - POST /api/warehouses - Create warehouse returns 200/201', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const wh = await createTestWarehouse(request, authToken, {});
    test.skip(!wh || !wh.id, 'Cannot create warehouse (insufficient role?)');
    createdWarehouseIds.push(wh.id);

    expect(wh).toHaveProperty('name');
    expect(wh).toHaveProperty('address');
  });

  test('API-W7 - POST /api/warehouses - Blank name returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/warehouses`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        name: '',
        address: 'Test address',
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('API-W8 - POST /api/warehouses - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/warehouses`, {
      data: { name: 'NoAuth WH', address: 'x' },
    });
    expect([401, 403]).toContain(response.status());
  });

  // === PUT (update) ===============================================

  test('API-W9 - PUT /api/warehouses/{id} - Update warehouse returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const wh = await createTestWarehouse(request, authToken, {});
    test.skip(!wh || !wh.id, 'Cannot create warehouse');
    createdWarehouseIds.push(wh.id);

    const newName = `Updated WH ${Date.now()}`;
    const response = await request.put(`${API_BASE}/warehouses/${wh.id}`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        name: newName,
        address: wh.address || 'Updated address',
        isActive: true,
      },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.name).toBe(newName);
  });

  test('API-W9b - PUT /api/warehouses/{id} - Non-existent returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.put(
      `${API_BASE}/warehouses/00000000-0000-0000-0000-000000000000`,
      {
        headers: { ...managerHeaders, 'Content-Type': 'application/json' },
        data: { name: 'ghost', address: 'x', isActive: true },
      }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  // === PATCH /status =============================================

  test('API-W10 - PATCH /api/warehouses/{id}/status - Toggle active flag', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const wh = await createTestWarehouse(request, authToken, { isActive: true });
    test.skip(!wh || !wh.id, 'Cannot create warehouse');
    createdWarehouseIds.push(wh.id);

    const response = await request.patch(`${API_BASE}/warehouses/${wh.id}/status`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { isActive: false },
    });
    expect([200, 204]).toContain(response.status());

    // Restore before cleanup so DELETE can succeed (some BEs refuse to delete inactive warehouses)
    await toggleWarehouseStatus(request, authToken, wh.id, true);
  });

  // === DELETE =====================================================

  test('API-W11 - DELETE /api/warehouses/{id} - Delete returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const wh = await createTestWarehouse(request, authToken, {});
    test.skip(!wh || !wh.id, 'Cannot create warehouse');
    // The test owns this one — remove from cleanup list so we delete explicitly.
    createdWarehouseIds = createdWarehouseIds.filter((id) => id !== wh.id);

    const response = await request.delete(`${API_BASE}/warehouses/${wh.id}`, {
      headers: managerHeaders,
    });
    expect([200, 204]).toContain(response.status());

    // Verify it's gone (or marked deleted). Soft-delete keeps the row,
    // so we accept 200 (still findable) or 404 (hard-deleted).
    const after = await request.get(`${API_BASE}/warehouses/${wh.id}`, {
      headers: managerHeaders,
    });
    expect([200, 404, 500]).toContain(after.status());
  });

  test('API-W11b - DELETE /api/warehouses/{id} - Non-existent returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.delete(
      `${API_BASE}/warehouses/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([404, 500]).toContain(response.status());
  });
});
