const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Sync Log API Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // SYNC-1
  test('SYNC-1 - GET /api/sync-logs - Returns paginated sync logs', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/sync-logs?page=0&size=20`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // SYNC-2
  test('SYNC-2 - GET /api/sync-logs?status=SYNCED - Filter by status', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/sync-logs?status=SYNCED&page=0&size=20`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // SYNC-3
  test('SYNC-3 - GET /api/sync-logs?channelId={uuid} - Filter by channel', async ({ request, managerHeaders }) => {
    const channelId = '00000000-0000-0000-0000-000000000001';
    const response = await request.get(
      `${API_BASE}/sync-logs?channelId=${channelId}&page=0&size=20`,
      { headers: managerHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // SYNC-4
  test('SYNC-4 - GET /api/sync-logs?status=FAILED&channelId={uuid} - Combine filters', async ({ request, managerHeaders }) => {
    const channelId = '00000000-0000-0000-0000-000000000001';
    const response = await request.get(
      `${API_BASE}/sync-logs?status=FAILED&channelId=${channelId}&page=0&size=20`,
      { headers: managerHeaders }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // SYNC-5
  test('SYNC-5 - GET /api/sync-logs?status=INVALID - Invalid status returns 4xx/5xx', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/sync-logs?status=NOT_A_VALID_STATUS`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
  });
});
