/**
 * API tests for ShopifyOAuthController (/api/channels/shopify).
 *
 * Covers:
 *   - GET /api/channels/shopify/authorize
 *   - GET /api/channels/shopify/callback
 *
 * The OAuth exchange itself requires real Shopify credentials, so we
 * exercise the URL builder and the redirect/error branches, not the
 * real token exchange.
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');

test.describe('Shopify OAuth API Tests', () => {
  // ─────────────────────────  /authorize  ─────────────────────────

  test('SHO-1 - GET /authorize with valid shop returns 200 and URL payload', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/channels/shopify/authorize?shop=test-shop.myshopify.com`,
      { headers: managerHeaders, maxRedirects: 0 },
    );
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('url');
    expect(body.data.url).toContain('myshopify.com');
    expect(body.data.url).toContain('client_id=');
    expect(body.data.url).toContain('redirect_uri=');
  });

  test('SHO-2 - GET /authorize without auth returns 401/403', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/shopify/authorize?shop=test-shop.myshopify.com`,
      { maxRedirects: 0 },
    );
    expect([401, 403]).toContain(res.status());
  });

  test('SHO-3 - GET /authorize with another valid shop still returns a URL', async ({ request, managerHeaders }) => {
    const res = await request.get(
      `${API_BASE}/channels/shopify/authorize?shop=another-shop.myshopify.com`,
      { headers: managerHeaders, maxRedirects: 0 },
    );
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.data.url).toContain('another-shop');
  });

  // ─────────────────────────  /callback  ─────────────────────────

  test('SHO-4 - GET /callback without required params returns 500 (code missing)', async ({ request }) => {
    // The controller declares @RequestParam code as required; missing it
    // surfaces as a 500 (MissingServletRequestParameterException) under
    // the configured error handler.
    const res = await request.get(`${API_BASE}/channels/shopify/callback`, {
      maxRedirects: 0,
    });
    expect([400, 500]).toContain(res.status());
  });

  test('SHO-5 - GET /callback with invalid shop returns 302 to error page', async ({ request }) => {
    // The exchange will fail because the code is invalid, so the catch
    // branch is exercised.
    const res = await request.get(
      `${API_BASE}/channels/shopify/callback?code=fake_code&shop=invalid-shop`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toMatch(/error=(oauth_failed|channel_identity_conflict)/);
  });

  test('SHO-6 - GET /callback with an obviously fake code still redirects to error', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/channels/shopify/callback?code=invalid_code&shop=test-shop.myshopify.com`,
      { maxRedirects: 0 },
    );
    expect(res.status()).toBe(302);
    const location = res.headers()['location'] || res.headers()['Location'] || '';
    expect(location).toContain('/channels');
  });
});
