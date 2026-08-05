const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');

test.describe('TikTok OAuth API Tests', () => {
  test('TT-1 - GET /callback without code returns 302 to error', async ({ request }) => {
    const res = await request.get(`${API_BASE}/channels/tiktok/callback`, {
      maxRedirects: 0,
    });
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=tiktok_oauth_failed');
  });

  test('TT-2 - GET /callback with error=access_denied returns 302 to error', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/tiktok/callback?error=access_denied`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=tiktok_oauth_failed');
  });

  test('TT-3 - GET /callback with empty code returns 302 to error', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/tiktok/callback?code=`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=tiktok_oauth_failed');
  });

  test('TT-4 - GET /callback with invalid code returns 302 to error', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/tiktok/callback?code=invalid_code_no_exchange`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=');
  });

  test('TT-5 - GET /callback with error param takes precedence over code', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/tiktok/callback?code=any&error=user_cancelled`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('error=tiktok_oauth_failed');
  });
});
