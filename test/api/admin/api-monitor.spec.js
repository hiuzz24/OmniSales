const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/admin-helpers');

test.describe('API Monitor API Tests (admin role)', () => {

  // MON-1
  test('MON-1 - GET /api/admin/monitor/summary - Returns summary metrics', async ({ request, adminHeaders }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/summary`, {
      headers: adminHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toBeTruthy();
  });

  // MON-2
  test('MON-2 - GET /api/admin/monitor/traffic?range=today - Traffic today', async ({ request, adminHeaders }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/traffic?range=today`, {
      headers: adminHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // MON-3
  test('MON-3 - GET /api/admin/monitor/traffic?range=7days - Traffic 7 days', async ({ request, adminHeaders }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/traffic?range=7days`, {
      headers: adminHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // MON-4
  test('MON-4 - GET /api/admin/monitor/endpoints - Lists endpoint metrics', async ({ request, adminHeaders }) => {
    const response = await request.get(`${API_BASE}/admin/monitor/endpoints`, {
      headers: adminHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });
});
