const { test, expect } = require('@playwright/test');
const { getAuthToken, API_BASE } = require('../../utils/inventory-helpers');

test.describe('Notification API Tests', () => {

  let authToken;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
  });

  // NOTI-1
  test('NOTI-1 - GET /api/notifications - Returns paginated list', async ({ request }) => {
    const response = await request.get(`${API_BASE}/notifications?page=0&size=20`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // NOTI-2
  test('NOTI-2 - GET /api/notifications?unreadOnly=true - Filters unread only', async ({ request }) => {
    const response = await request.get(`${API_BASE}/notifications?unreadOnly=true&page=0&size=20`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // NOTI-3
  test('NOTI-3 - GET /api/notifications?userId={uuid} - Filter by userId', async ({ request }) => {
    const fakeUserId = '00000000-0000-0000-0000-000000000001';
    const response = await request.get(`${API_BASE}/notifications?userId=${fakeUserId}&page=0&size=20`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
  });

  // NOTI-4
  test('NOTI-4 - GET /api/notifications/unread-count - Returns unread count', async ({ request }) => {
    const response = await request.get(`${API_BASE}/notifications/unread-count`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(typeof body.data).toBe('number');
  });

  // NOTI-5
  test('NOTI-5 - GET /api/notifications/unread-count?userId={uuid} - Count unread for user', async ({ request }) => {
    const fakeUserId = '00000000-0000-0000-0000-000000000001';
    const response = await request.get(`${API_BASE}/notifications/unread-count?userId=${fakeUserId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(typeof body.data).toBe('number');
  });

  // NOTI-6
  test('NOTI-6 - PATCH /api/notifications/{id}/read - Mark as read success', async ({ request }) => {
    const response = await request.patch(`${API_BASE}/notifications/00000000-0000-0000-0000-000000000099/read`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect([200, 404]).toContain(response.status());
  });

  // NOTI-7
  test('NOTI-7 - PATCH /api/notifications/{invalidId}/read - Returns 4xx for invalid UUID', async ({ request }) => {
    const response = await request.patch(`${API_BASE}/notifications/not-a-valid-uuid/read`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  // NOTI-8
  test('NOTI-8 - POST /api/notifications/mark-all-read?userId={uuid} - Mark all as read', async ({ request }) => {
    const fakeUserId = '00000000-0000-0000-0000-000000000001';
    const response = await request.post(`${API_BASE}/notifications/mark-all-read?userId=${fakeUserId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(typeof body.data).toBe('number');
  });
});
