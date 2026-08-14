const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');

const FAKE_PRODUCT = '00000000-0000-0000-0000-000000000000';
const FAKE_CHANNEL = '11111111-1111-1111-1111-111111111111';

test.describe('ProductChannelConfig API Tests', () => {
  // ─────────────────────────  GET /config  ─────────────────────────

  test('PCC-1 - GET /config without auth returns 401/403', async ({ request }) => {
    const res = await request.get(`${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`);
    expect([401, 403]).toContain(res.status());
  });

  test('PCC-2 - GET /config with random productId+channelId returns 404 (no mapping)', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`,
      { headers: managerHeaders },
    );
    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.message).toContain('mapping');
  });

  test('PCC-3 - GET /config with malformed productId returns 400/500', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/products/not-a-uuid/channels/${FAKE_CHANNEL}/config`,
      { headers: managerHeaders },
    );
    expect([400, 500]).toContain(res.status());
  });

  test('PCC-4 - GET /config with malformed channelId returns 400/500', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/not-a-uuid/config`,
      { headers: managerHeaders },
    );
    expect([400, 500]).toContain(res.status());
  });

  // ─────────────────────────  PUT /config  ─────────────────────────

  test('PCC-5 - PUT /config without auth returns 401/403', async ({ request }) => {
    const res = await request.put(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`,
      { data: { channelId: FAKE_CHANNEL } },
    );
    expect([401, 403]).toContain(res.status());
  });

  test('PCC-6 - PUT /config with random productId+channelId returns 404 (no mapping)', async ({ request, managerHeaders }) => {
    const res = await request.put(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`,
      { headers: managerHeaders, data: { channelId: FAKE_CHANNEL } },
    );
    expect(res.status()).toBe(404);
  });

  test('PCC-7 - PUT /config with mismatched channelId in body returns 400/404', async ({ request, managerHeaders }) => {
    // The request body includes channelId; if it doesn't match the path channelId, the service should reject.
    const res = await request.put(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`,
      {
        headers: managerHeaders,
        data: { channelId: '22222222-2222-2222-2222-222222222222' },
      },
    );
    expect([400, 404]).toContain(res.status());
  });

  test('PCC-8 - PUT /config with empty body returns 4xx', async ({ request, managerHeaders }) => {
    const res = await request.put(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`,
      { headers: managerHeaders, data: {} },
    );
    expect([400, 404, 500]).toContain(res.status());
  });

  // ─────────────────────────  Cross-endpoint consistency  ─────────────────

  test('PCC-9 - GET and PUT return 404 for the same non-existent mapping', async ({ request, managerHeaders }) => {
    const getRes = await request.get(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`,
      { headers: managerHeaders },
    );
    expect(getRes.status()).toBe(404);

    const putRes = await request.put(
      `${API_BASE}/products/${FAKE_PRODUCT}/channels/${FAKE_CHANNEL}/config`,
      { headers: managerHeaders, data: { channelId: FAKE_CHANNEL } },
    );
    expect(putRes.status()).toBe(404);
  });
});
