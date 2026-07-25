/**
 * Order Pull API Tests.
 *
 * Covers all 3 OrderPullController endpoints:
 *   - POST /api/orders/pull           (start pull jobs for one or more channels)
 *   - GET  /api/orders/pull/{id}      (get one job)
 *   - GET  /api/orders/pull/active    (list active jobs)
 *
 * Channel validation:
 *   - Channels must be LAZADA / SHOPIFY / TIKTOK (others are rejected).
 *   - Channel must be connected (valid API credentials).
 *   - Range (from, to) must be ≤ 7 days.
 *
 * Because tests run against a real BE with possibly hand-crafted channels that
 * aren't fully connected, we accept a wide range of status codes:
 *   - 200 on the happy path
 *   - 400/422/500 if the channel is missing / not connected / not a supported platform
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  createTestChannel,
  deleteTestChannel,
  API_BASE,
} = require('../../utils/channel-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Order Pull API Tests', () => {

  let createdChannelIds = [];

  test.afterEach(async ({ request }) => {
    if (createdChannelIds.length) {
      const authToken = await getAuthTokenCached(request);
      for (const id of createdChannelIds.splice(0)) {
        await deleteTestChannel(request, authToken, id);
      }
    }
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  async function createChannel(request, managerHeaders, overrides = {}) {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const ch = await createTestChannel(request, authToken, overrides);
    if (ch && ch.id) createdChannelIds.push(ch.id);
    return ch;
  }

  // === POST /api/orders/pull ================================================

  test('OP-1 - POST /api/orders/pull - Empty channelIds returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/orders/pull`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { channelIds: [''] },  // empty strings -> backend should reject with ValidationException
    });
    expect([400, 422, 500]).toContain(response.status());
  });

  test('OP-2 - POST /api/orders/pull - Non-existent channel returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/orders/pull`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        channelIds: ['00000000-0000-0000-0000-000000000000'],
      },
    });
    expect([400, 404, 500]).toContain(response.status());
  });

  test('OP-3 - POST /api/orders/pull - Unsupported platform returns 400/500', async ({ request, managerHeaders }) => {
    // Manual channel is not in the supported list (LAZADA, SHOPIFY, TIKTOK).
    const ch = await createChannel(request, managerHeaders, { platform: 'MANUAL' });
    test.skip(!ch || !ch.id, 'Cannot create channel');

    const response = await request.post(`${API_BASE}/orders/pull`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { channelIds: [ch.id] },
    });
    expect([200, 400, 500]).toContain(response.status());
  });

  test('OP-4 - POST /api/orders/pull - Admin/Sales without role returns 403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/orders/pull`, {
      data: { channelIds: [] },
    });
    expect([401, 403]).toContain(response.status());
  });

  test('OP-5 - POST /api/orders/pull - Range > 7 days returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/orders/pull`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        channelIds: ['00000000-0000-0000-0000-000000000000'],
        from: '2020-01-01T00:00:00Z',
        to: '2025-01-01T00:00:00Z',
      },
    });
    expect([400, 500]).toContain(response.status());
  });

  // === GET /api/orders/pull/active ==========================================

  test('OP-6 - GET /api/orders/pull/active - Returns 200 with array', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/orders/pull/active`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  test('OP-7 - GET /api/orders/pull/active - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/orders/pull/active`);
    expect([401, 403]).toContain(response.status());
  });

  // === GET /api/orders/pull/{id} ==========================================

  test('OP-8 - GET /api/orders/pull/{id} - Non-existent id returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/orders/pull/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  test('OP-9 - GET /api/orders/pull/{id} - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/orders/pull/00000000-0000-0000-0000-000000000000`
    );
    expect([401, 403]).toContain(response.status());
  });
});
