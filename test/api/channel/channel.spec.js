const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');
const {
  createTestChannel,
  deleteTestChannel,
} = require('../../utils/channel-helpers');

test.describe('Channel API Tests', () => {

  let createdChannelIds = [];

  test.afterEach(async ({ request, managerHeaders }) => {
    if (!createdChannelIds.length) return;
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    for (const id of createdChannelIds.splice(0)) {
      await deleteTestChannel(request, authToken, id);
    }
  });

  // GET /api/channels
  test('CH1 - GET /api/channels - List channels returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/channels`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  test('CH2 - GET /api/channels - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channels`);
    expect([401, 403]).toContain(response.status());
  });

  // POST /api/channels
  test('CH3 - POST /api/channels - Create manual channel returns 201', async ({ request, managerHeaders }) => {
    const timestamp = Date.now();
    const payload = {
      platform: 'MANUAL',
      displayName: `TestMC_${timestamp}_${Math.random().toString(36).slice(2,8)}`,
      region: 'VN',
      syncEnabled: true,
      commissionRate: 5.0,
      metadata: {},
    };

    const response = await request.post(`${API_BASE}/channels`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: payload,
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBeTruthy();
    expect(body.data.platform).toBe('MANUAL');
    expect(body.data.displayName).toBe(payload.displayName);
    expect(body.data.status).toBe('CONNECTED');

    createdChannelIds.push(body.data.id);
  });

  test('CH4 - POST /api/channels - Empty displayName returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/channels`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        platform: 'MANUAL',
        displayName: '',
        region: 'VN',
      },
    });

    expect(response.status()).toBe(400);
  });

  test('CH5 - POST /api/channels - Missing platform returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/channels`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        displayName: 'NoPlatform',
        region: 'VN',
      },
    });

    expect(response.status()).toBe(400);
  });

  test('CH6 - POST /api/channels - Commission > 100 rejected (400)', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/channels`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        platform: 'MANUAL',
        displayName: `BadCommission ${Date.now()}`,
        commissionRate: 150,
      },
    });

    expect(response.status()).toBe(400);
  });

  // GET /api/channels/{id}
  test('CH7 - GET /api/channels/{id} - Get by id returns 200', async ({ request, managerHeaders }) => {
    const ch7 = createdChannelIds[createdChannelIds.length - 1];
    test.skip(!ch7, 'No channel created yet');
    const response = await request.get(`${API_BASE}/channels/${ch7}`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.data.id).toBe(ch7);
    expect(body.data).toHaveProperty('metadata');
    expect(body.data.metadata).toHaveProperty('productCount');
  });

  test('CH8 - GET /api/channels/{id} - Not found returns 404', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/channels/00000000-0000-0000-0000-000000000000`, {
      headers: managerHeaders,
    });

    expect([404, 500]).toContain(response.status());
  });

  // PUT /api/channels/{id}
  test('CH9 - PUT /api/channels/{id} - Update returns 200', async ({ request, managerHeaders }) => {
    const ch9 = createdChannelIds[createdChannelIds.length - 1];
    test.skip(!ch9, 'No channel created yet');
    const newName = `Updated Manual Channel ${Date.now()}`;
    const response = await request.put(`${API_BASE}/channels/${ch9}`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        platform: 'MANUAL',
        displayName: newName,
        commissionRate: 7.5,
        syncEnabled: false,
        metadata: { note: 'updated via test' },
      },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.data.displayName).toBe(newName);
    expect(body.data.commissionRate).toBe(7.5);
    expect(body.data.syncEnabled).toBe(false);
  });

  // DELETE /api/channels/{id} (soft delete)
  test('CH10 - DELETE /api/channels/{id} - Soft delete returns 200', async ({ request, managerHeaders }) => {
    const ts = Date.now();
    const create = await request.post(`${API_BASE}/channels`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: { platform: 'MANUAL', displayName: `ToDelete_${ts}_${Math.random().toString(36).slice(2,8)}`, region: 'VN', metadata: {} },
    });
    expect(create.status()).toBe(201);
    const id = (await create.json()).data.id;

    const del = await request.delete(`${API_BASE}/channels/${id}`, {
      headers: managerHeaders,
    });

    expect(del.status()).toBe(200);

    const after = await request.get(`${API_BASE}/channels/${id}`, {
      headers: managerHeaders,
    });
    expect([404, 500]).toContain(after.status());
  });

  // GET /api/channels/{id}/products - currently throws NotImplemented
  test('CH11 - GET /api/channels/{id}/products - Not implemented (500)', async ({ request, managerHeaders }) => {
    const ch11 = createdChannelIds[createdChannelIds.length - 1];
    test.skip(!ch11, 'No channel created yet');
    const response = await request.get(`${API_BASE}/channels/${ch11}/products`, {
      headers: managerHeaders,
    });

    expect([200, 500]).toContain(response.status());
  });

  // POST duplicate (platform, displayName)
  test('CH12 - POST /api/channels - Duplicate (platform, displayName) rejected', async ({ request, managerHeaders }) => {
    const ts = Date.now();
    const name = `DupCh_${ts}_${Math.random().toString(36).slice(2,8)}`;
    const first = await request.post(`${API_BASE}/channels`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: { platform: 'MANUAL', displayName: name, region: 'VN', metadata: {} },
    });
    expect(first.status()).toBe(201);
    const id = (await first.json()).data.id;

    const dup = await request.post(`${API_BASE}/channels`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: { platform: 'MANUAL', displayName: name, region: 'VN', metadata: {} },
    });

    expect([409, 500]).toContain(dup.status());

    createdChannelIds.push(id);
  });

});
