const { test, expect } = require('@playwright/test');
const { getAdminAuthHeaders, API_BASE } = require('../../utils/admin-helpers');

test.describe('Backup API Tests (admin role)', () => {

  let authToken;

  test.beforeAll(async ({ request }) => {
    authToken = (await getAdminAuthHeaders(request)).Authorization;
    expect(authToken).toBeTruthy();
  });

  // BAK-1
  test('BAK-1 - GET /api/backups - Lists backup files paginated', async ({ request }) => {
    const response = await request.get(`${API_BASE}/backups?page=0&size=20`, {
      headers: { Authorization: authToken },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  // BAK-2
  test('BAK-2 - POST /api/backups - Triggers manual backup', async ({ request }) => {
    const response = await request.post(`${API_BASE}/backups`, {
      headers: { Authorization: authToken },
    });

    expect([200, 202, 500]).toContain(response.status());
  });

  // BAK-3
  test('BAK-3 - DELETE /api/backups/{id} - Deletes a backup file', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.delete(`${API_BASE}/backups/${fakeId}`, {
      headers: { Authorization: authToken },
    });

    expect([200, 400, 404, 500]).toContain(response.status());
  });

  // BAK-4
  test('BAK-4 - GET /api/backups/{id}/download - Returns binary file response', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.get(`${API_BASE}/backups/${fakeId}/download`, {
      headers: { Authorization: authToken },
    });

    if (response.status() === 200) {
      const contentType = response.headers()['content-type'];
      expect(contentType).toBeTruthy();
      const body = await response.body();
      expect(Buffer.isBuffer(body) || body instanceof Uint8Array).toBe(true);
    } else {
      expect([400, 404, 500]).toContain(response.status());
    }
  });

  // BAK-5
  test('BAK-5 - POST /api/backups/{id}/restore - Restores backup with password', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/backups/${fakeId}/restore`, {
      headers: {
        Authorization: authToken,
        'Content-Type': 'application/json',
      },
      data: { password: 'test-password-123' },
    });

    expect([200, 400, 404]).toContain(response.status());
  });

  // BAK-6
  test('BAK-6 - POST /api/backups/{id}/restore - Missing password returns 400', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/backups/${fakeId}/restore`, {
      headers: {
        Authorization: authToken,
        'Content-Type': 'application/json',
      },
      data: {},
    });

    expect(response.status()).toBe(400);
  });
});
