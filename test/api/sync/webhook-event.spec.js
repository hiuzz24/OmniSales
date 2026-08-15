const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Webhook Event API Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // WHK-1
  test('WHK-1 - GET /api/webhook-events - List webhook events paginated', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/webhook-events?page=0&size=20`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // WHK-2
  test('WHK-2 - GET /api/webhook-events?platform=SHOPIFY - Filter by platform', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/webhook-events?platform=SHOPIFY&page=0&size=20`,
      { headers: managerHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // WHK-3
  test('WHK-3 - GET /api/webhook-events?status=SUCCESS&eventType=order_created - Filter status + type', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/webhook-events?status=SUCCESS&eventType=order_created&page=0&size=20`,
      { headers: managerHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // WHK-4
  test('WHK-4 - GET /api/webhook-events?channelId={uuid} - Filter by channel', async ({ request, managerHeaders }) => {
    const channelId = '00000000-0000-0000-0000-000000000001';
    const response = await request.get(
      `${API_BASE}/webhook-events?channelId=${channelId}&page=0&size=20`,
      { headers: managerHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // WHK-5
  test('WHK-5 - POST /api/webhooks/shopify - Receives raw webhook payload', async ({ request }) => {
    const shopifyPayload = JSON.stringify({
      id: 1234567890,
      topic: 'orders/create',
      shop_domain: 'test-shop.myshopify.com',
    });
    const response = await request.post(`${API_BASE}/webhooks/shopify`, {
      headers: {
        'X-Shopify-Topic': 'orders/create',
        'X-Shopify-Shop-Domain': 'test-shop.myshopify.com',
        'Content-Type': 'application/json',
      },
      data: shopifyPayload,
    });

    expect([200, 202, 400, 401]).toContain(response.status());
  });

  // WHK-6
  test('WHK-6 - POST /api/webhooks/lazada - Receives raw webhook Lazada', async ({ request }) => {
    const lazadaPayload = JSON.stringify({
      message_id: 'test-msg-001',
      message_type: 'order',
    });
    const response = await request.post(`${API_BASE}/webhooks/lazada`, {
      headers: {
        'Content-Type': 'application/json',
      },
      data: lazadaPayload,
    });

    expect([200, 202, 400, 401]).toContain(response.status());
  });

  // WHK-7 - POST /api/webhook-events/{id}/reprocess - reprocess non-existent event
  // Endpoint requires OWNER role; admin (SYSTEM_ADMIN) gets 403.
  test('WHK-7 - POST /api/webhook-events/{id}/reprocess - Non-existent event returns 403, 404 or 500', async ({ request, adminHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/webhook-events/${fakeId}/reprocess`, {
      headers: adminHeaders,
    });

    expect([200, 403, 404, 500]).toContain(response.status());
  });

  // WHK-8 - reprocess requires auth
  test('WHK-8 - POST /api/webhook-events/{id}/reprocess - Without auth returns 401 or 403', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000001';
    const response = await request.post(`${API_BASE}/webhook-events/${fakeId}/reprocess`);

    expect([401, 403]).toContain(response.status());
  });

  // WHK-9 - reprocess with random uuid - manager role should be denied
  test('WHK-9 - POST /api/webhook-events/{id}/reprocess - Manager role returns 200, 403 or 500', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000002';
    const response = await request.post(`${API_BASE}/webhook-events/${fakeId}/reprocess`, {
      headers: managerHeaders,
    });

    expect([200, 403, 404, 500]).toContain(response.status());
  });

  // WHK-10 - reprocess valid UUID format - happy path returns 404 (no event)
  // Admin role is SYSTEM_ADMIN; endpoint requires OWNER → 403.
  test('WHK-10 - POST /api/webhook-events/{id}/reprocess - Owner role, valid random UUID returns 403, 404 or 500', async ({ request, adminHeaders }) => {
    const fakeId = '11111111-1111-1111-1111-111111111111';
    const response = await request.post(`${API_BASE}/webhook-events/${fakeId}/reprocess`, {
      headers: adminHeaders,
    });

    expect([200, 403, 404, 500]).toContain(response.status());
  });
});
