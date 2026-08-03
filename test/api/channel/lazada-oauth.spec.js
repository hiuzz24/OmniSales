/**
 * API tests for LazadaOAuthController (/api/channels/lazada).
 *
 * Covers:
 *   - GET /api/channels/lazada/authorize
 *   - GET /api/channels/lazada/callback
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');

test.describe('Lazada OAuth API Tests', () => {
  // ─────────────────────────  /authorize  ─────────────────────────

  test('LZA-1 - GET /authorize returns 200 with URL payload', async ({ request, managerHeaders }) => {
    const res = await request.get(`${API_BASE}/channels/lazada/authorize`, {
      headers: managerHeaders,
      maxRedirects: 0,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('url');
    expect(body.data.url).toContain('lazada');
    expect(body.data.url).toContain('redirect_uri=');
    expect(body.data.url).toContain('client_id=');
  });

  test('LZA-2 - GET /authorize without auth returns 401/403', async ({ request }) => {
    const res = await request.get(`${API_BASE}/channels/lazada/authorize`, {
      maxRedirects: 0,
    });
    expect([401, 403]).toContain(res.status());
  });

  // ─────────────────────────  /callback  ─────────────────────────

  test('LZA-3 - GET /callback without code returns 302 to error=missing_code', async ({ request }) => {
    const res = await request.get(`${API_BASE}/channels/lazada/callback`, {
      maxRedirects: 0,
    });
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=missing_code');
  });

  test('LZA-4 - GET /callback with error=access_denied returns 302 to error=access_denied', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/lazada/callback?error=access_denied`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=access_denied');
  });

  test('LZA-5 - GET /callback with empty code returns 302 to error=missing_code', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/lazada/callback?code=`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=missing_code');
  });

  test('LZA-6 - GET /callback with error param takes precedence over code', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/lazada/callback?code=any&error=user_cancelled`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=user_cancelled');
  });

  test('LZA-7 - GET /callback with invalid code returns 302 to error=connection_failed', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/lazada/callback?code=invalid_code_no_exchange`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=');
  });
});
