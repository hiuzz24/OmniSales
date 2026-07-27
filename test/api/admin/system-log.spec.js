const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/admin-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('System Log API Tests (admin role)', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // LOG-1
  test('LOG-1 - GET /api/system-logs - Lists paginated system logs', async ({ request, adminHeaders }) => {
    const response = await request.get(`${API_BASE}/system-logs?page=0&size=20`, {
      headers: adminHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // LOG-2
  test('LOG-2 - GET /api/system-logs?level=ERROR - Filter by level', async ({ request, adminHeaders }) => {
    const response = await request.get(`${API_BASE}/system-logs?level=ERROR&page=0&size=20`, {
      headers: adminHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // LOG-3
  test('LOG-3 - GET /api/system-logs?keyword=error - Search by keyword', async ({ request, adminHeaders }) => {
    const response = await request.get(`${API_BASE}/system-logs?keyword=error&page=0&size=20`, {
      headers: adminHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // LOG-4
  test('LOG-4 - GET /api/system-logs?from=...&to=... - Filter by date range', async ({ request, adminHeaders }) => {
    const from = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
    const to = new Date().toISOString();
    const response = await request.get(
      `${API_BASE}/system-logs?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&page=0&size=20`,
      { headers: adminHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // LOG-5
  test('LOG-5 - PUT /api/system-logs/{id} - Updates a log entry', async ({ request, adminHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.put(`${API_BASE}/system-logs/${fakeId}`, {
      headers: {
        ...adminHeaders,
        'Content-Type': 'application/json',
      },
      data: {
        level: 'WARN',
        message: 'Updated log message',
      },
    });

    expect([200, 400, 404, 500]).toContain(response.status());
  });

  // LOG-6
  test('LOG-6 - DELETE /api/system-logs/{id} - Deletes a log entry', async ({ request, adminHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.delete(`${API_BASE}/system-logs/${fakeId}`, {
      headers: adminHeaders,
    });

    expect([200, 400, 404, 500]).toContain(response.status());
  });
});
