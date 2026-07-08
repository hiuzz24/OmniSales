const { test, expect } = require('@playwright/test');
const { getAdminAuthHeaders, API_BASE } = require('../../utils/admin-helpers');

test.describe('API Monitor API Tests (admin role)', () => {

  let authToken;

  test.beforeAll(async ({ request }) => {
    authToken = (await getAdminAuthHeaders(request)).Authorization;
    expect(authToken).toBeTruthy();
  });

  // MON-1
  test('MON-1 - GET /api/admin/monitor/summary - Returns summary metrics', async ({ request }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/summary`, {
      headers: { Authorization: authToken },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toBeTruthy();
  });

  // MON-2
  test('MON-2 - GET /api/admin/monitor/traffic?range=today - Traffic today', async ({ request }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/traffic?range=today`, {
      headers: { Authorization: authToken },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // MON-3
  test('MON-3 - GET /api/admin/monitor/traffic?range=7days - Traffic 7 days', async ({ request }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/traffic?range=7days`, {
      headers: { Authorization: authToken },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // MON-4
  test('MON-4 - GET /api/admin/monitor/endpoints - Lists endpoint metrics', async ({ request }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/endpoints`, {
      headers: { Authorization: authToken },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });
});
