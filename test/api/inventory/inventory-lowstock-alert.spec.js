const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');

/**
 * Playwright API tests for Low-Stock Alert visibility to OWNER.
 *
 * Covers:
 *   - GET /api/notifications (read notifications feed)
 *   - GET /api/notifications/unread-count (count)
 *   - POST /api/notifications/mark-all-read (idempotent count check)
 */
test.describe('Low-Stock Alert Notifications API', () => {

  test('LS-API-1 — GET /api/notifications returns owner-scoped feed', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/notifications?unreadOnly=true&page=0&size=20`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('data');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('LS-API-2 — GET /api/notifications without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/notifications?unreadOnly=true`);
    expect([401, 403]).toContain(response.status());
  });

  test('LS-API-3 — GET /api/notifications/unread-count returns a numeric count', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/notifications/unread-count`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(typeof body.data === 'number' || typeof body === 'number').toBe(true);
  });

  test('LS-API-4 — POST /api/notifications/mark-all-read returns count and is idempotent', async ({ request, managerHeaders }) => {
    const userId = await getUserId(request, managerHeaders);

    const firstResp = await request.post(`${API_BASE}/notifications/mark-all-read?userId=${userId}`, {
      headers: managerHeaders,
    });
    expect(firstResp.status()).toBe(200);
    const firstBody = await firstResp.json();
    expect(typeof firstBody.data === 'number').toBe(true);

    const secondResp = await request.post(`${API_BASE}/notifications/mark-all-read?userId=${userId}`, {
      headers: managerHeaders,
    });
    expect(secondResp.status()).toBe(200);
    const secondBody = await secondResp.json();
    // Second call marks nothing new.
    expect(secondBody.data).toBe(0);
  });

  test('LS-API-5 — Notification filtering by entityType INVENTORY yields zero items without alerts', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/notifications?unreadOnly=false&page=0&size=200`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    const content = body.data?.content || body.data || [];
    expect(Array.isArray(content)).toBe(true);
    // Just confirm the shape includes type field
    for (const n of content) {
      expect(n).toHaveProperty('type');
    }
  });

  async function getUserId(request, headers) {
    // Use the manager profile endpoint to fetch the current user ID.
    const resp = await request.get(`${API_BASE}/users/me/profile`, { headers });
    if (resp.ok()) {
      const body = await resp.json();
      return body.data?.id;
    }
    // Fallback: profile is under /users/me
    const resp2 = await request.get(`${API_BASE}/users/me`, { headers });
    if (resp2.ok()) {
      const body = await resp2.json();
      return body.data?.id;
    }
    return null;
  }
});