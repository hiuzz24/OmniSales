const { test, expect } = require('@playwright/test');
const { getAuthToken } = require('../utils/customer-helpers');
const { API_BASE: ENV_API_BASE } = require('../utils/env-config');

const API_BASE = process.env.API_BASE || ENV_API_BASE;

test.describe('Channel Connection Logs API Tests', () => {

  let authToken;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
  });

  // GET /api/channel-connection-logs

  test('CCL1 - GET /api/channel-connection-logs - List logs returns 200', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?page=0&size=20`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(body.data).toHaveProperty('totalElements');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('CCL2 - GET /api/channel-connection-logs - Filter by platform=SHOPIFY', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?platform=SHOPIFY&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('CCL3 - GET /api/channel-connection-logs - Filter by platform=LAZADA', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?platform=LAZADA&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('CCL4 - GET /api/channel-connection-logs - Filter by status=SUCCESS', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?status=SUCCESS&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('CCL5 - GET /api/channel-connection-logs - Filter by status=FAILED', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?status=FAILED&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('CCL6 - GET /api/channel-connection-logs - Filter by action=CONNECT', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?action=CONNECT&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('CCL7 - GET /api/channel-connection-logs - Filter by action=DISCONNECT', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?action=DISCONNECT&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('CCL8 - GET /api/channel-connection-logs - Filter by channelId', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs?channelId=00000000-0000-0000-0000-000000000000&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });

  test('CCL9 - GET /api/channel-connection-logs - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/channel-connection-logs`);
    expect([401, 403]).toContain(response.status());
  });

  test('CCL10 - GET /api/channel-connection-logs - Combined filters', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/channel-connection-logs?platform=SHOPIFY&status=SUCCESS&action=CONNECT&page=0&size=10`,
      { headers: { Authorization: `Bearer ${authToken}` } },
    );

    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
  });
});
