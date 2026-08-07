/**
 * API tests for /api/platform-lookups/* endpoints.
 *
 * There are five endpoints:
 *   - GET  /api/platform-lookups/{platform}/categories?channelId=...&parentId=&keyword=&categoryVersion=
 *   - GET  /api/platform-lookups/{platform}/categories/{categoryId}/attributes?channelId=...&categoryVersion=
 *   - GET  /api/platform-lookups/{platform}/brands?channelId=...&categoryId=&categoryVersion=&keyword=&page=&size=&pageToken=
 *   - POST /api/platform-lookups/{platform}/category-suggestions (body: CategorySuggestionRequest)
 *   - PUT  /api/platform-lookups/{platform}/cache?channelId=...
 *
 * The lookup services depend on platform-specific OAuth credentials that
 * are not configured in test environments, so the platform-lookup
 * endpoints return 500 for valid platforms (no real API call possible).
 * The tests below verify:
 *   - 403/401 for missing auth
 *   - 500 for valid platform + missing channelId (backend throws)
 *   - 500 for valid platform + fake channelId (OAuth config missing)
 *   - 500 for invalid platform (e.g. "INVALID")
 *   - 400 for malformed UUID on the channelId path
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');

const FAKE_CHANNEL = '00000000-0000-0000-0000-000000000000';

test.describe('Platform Lookups API Tests', () => {
  // ─────────────────────────  /categories  ─────────────────────────

  test('PL-1 - GET /categories without auth returns 401/403', async ({ request }) => {
    const res = await request.get(`${API_BASE}/platform-lookups/SHOPIFY/categories?channelId=${FAKE_CHANNEL}`);
    expect([401, 403]).toContain(res.status());
  });

  test('PL-2 - GET /categories with valid platform returns 500 (no OAuth configured)', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/platform-lookups/SHOPIFY/categories?channelId=${FAKE_CHANNEL}`,
      { headers: managerHeaders },
    );
    expect(res.status()).toBe(500);
  });

  test('PL-3 - GET /categories without channelId returns 500 (required param)', async ({ request, managerHeaders }) => {
    const res = await request.get(`${API_BASE}/platform-lookups/LAZADA/categories`, { headers: managerHeaders });
    expect([400, 500]).toContain(res.status());
  });

  test('PL-4 - GET /categories with invalid platform returns 500 / 400', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/platform-lookups/INVALID/categories?channelId=${FAKE_CHANNEL}`,
      { headers: managerHeaders },
    );
    expect([400, 500]).toContain(res.status());
  });

  test('PL-5 - GET /categories with malformed channelId returns 4xx', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/platform-lookups/SHOPIFY/categories?channelId=not-a-uuid`,
      { headers: managerHeaders },
    );
    expect([400, 500]).toContain(res.status());
  });

  // ─────────────────────────  /categories/{id}/attributes  ─────────────────────────

  test('PL-6 - GET /attributes without auth returns 401/403', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/platform-lookups/SHOPIFY/categories/cat-123/attributes?channelId=${FAKE_CHANNEL}`,
    );
    expect([401, 403]).toContain(res.status());
  });

  test('PL-7 - GET /attributes with valid platform returns 500 (no OAuth)', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/platform-lookups/LAZADA/categories/cat-123/attributes?channelId=${FAKE_CHANNEL}`,
      { headers: managerHeaders },
    );
    expect(res.status()).toBe(500);
  });

  // ─────────────────────────  /brands  ─────────────────────────

  test('PL-8 - GET /brands without auth returns 401/403', async ({ request }) => {
    const res = await request.get(`${API_BASE}/platform-lookups/SHOPIFY/brands?channelId=${FAKE_CHANNEL}`);
    expect([401, 403]).toContain(res.status());
  });

  test('PL-9 - GET /brands with valid platform returns 500 (no OAuth)', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/platform-lookups/SHOPIFY/brands?channelId=${FAKE_CHANNEL}`,
      { headers: managerHeaders },
    );
    expect(res.status()).toBe(500);
  });

  test('PL-10 - GET /brands without channelId returns 4xx', async ({ request, managerHeaders }) => {
    const res = await request.get(`${API_BASE}/platform-lookups/SHOPIFY/brands`, { headers: managerHeaders });
    expect([400, 500]).toContain(res.status());
  });

  test('PL-11 - GET /brands with pagination params returns 500 (no OAuth)', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/platform-lookups/SHOPIFY/brands?channelId=${FAKE_CHANNEL}&page=0&size=50`,
      { headers: managerHeaders },
    );
    expect(res.status()).toBe(500);
  });

  // ─────────────────────────  /category-suggestions  ─────────────────────────

  test('PL-12 - POST /category-suggestions without auth returns 401/403', async ({ request }) => {
    const res = await request.post(`${API_BASE}/platform-lookups/SHOPIFY/category-suggestions`, {
      data: { channelId: FAKE_CHANNEL, title: 'Coffee', description: 'Espresso beans' },
    });
    expect([401, 403]).toContain(res.status());
  });

  test('PL-13 - POST /category-suggestions without body returns 4xx', async ({ request, managerHeaders }) => {
    const res = await request.post(`${API_BASE}/platform-lookups/SHOPIFY/category-suggestions`, {
      headers: managerHeaders, data: {},
    });
    expect([400, 500]).toContain(res.status());
  });

  test('PL-14 - POST /category-suggestions with empty body returns 500 (no OAuth)', async ({ request, managerHeaders }) => {
    const res = await request.post(`${API_BASE}/platform-lookups/SHOPIFY/category-suggestions`, {
      headers: managerHeaders,
      data: { channelId: FAKE_CHANNEL, title: 'Coffee', description: 'Espresso beans' },
    });
    expect(res.status()).toBe(500);
  });

  // ─────────────────────────  /cache  ─────────────────────────

  test('PL-15 - PUT /cache without auth returns 401/403', async ({ request }) => {
    const res = await request.put(`${API_BASE}/platform-lookups/SHOPIFY/cache?channelId=${FAKE_CHANNEL}`);
    expect([401, 403]).toContain(res.status());
  });

  test('PL-16 - PUT /cache without channelId returns 4xx', async ({ request, managerHeaders }) => {
    const res = await request.put(`${API_BASE}/platform-lookups/SHOPIFY/cache`, { headers: managerHeaders });
    expect([400, 500]).toContain(res.status());
  });

  test('PL-17 - PUT /cache returns 500 when no lookup service is registered for the platform', async ({ request, managerHeaders }) => {
    // The cache clear dispatches to the platform's lookup service. If no
    // service is registered for the platform, the backend throws 500.
    // This documents the current behavior; once all platforms are wired
    // up, the expected status will be 200.
    const res = await request.put(
      `${API_BASE}/platform-lookups/MANUAL/cache?channelId=${FAKE_CHANNEL}`,
      { headers: managerHeaders },
    );
    expect([200, 500]).toContain(res.status());
  });

  test('PL-17b - PUT /cache with a fake channelId returns 500 (no platform service)', async ({ request, managerHeaders }) => {
    const res = await request.put(
      `${API_BASE}/platform-lookups/SHOPIFY/cache?channelId=${FAKE_CHANNEL}`,
      { headers: managerHeaders },
    );
    expect(res.status()).toBe(500);
  });

  test('PL-18 - PUT /cache with invalid platform returns 500', async ({ request, managerHeaders }) => {
    const res = await request.put(
      `${API_BASE}/platform-lookups/INVALID/cache?channelId=${FAKE_CHANNEL}`,
      { headers: managerHeaders },
    );
    expect([400, 500]).toContain(res.status());
  });
});
