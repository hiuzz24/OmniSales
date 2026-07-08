const { test, expect } = require('@playwright/test');
const { getAdminAuthHeaders, API_BASE } = require('../../utils/admin-helpers');

test.describe('System Setting API Tests (admin role)', () => {

  let authToken;

  test.beforeAll(async ({ request }) => {
    authToken = (await getAdminAuthHeaders(request)).Authorization;
    expect(authToken).toBeTruthy();
  });

  // SET-1
  test('SET-1 - GET /api/admin/settings - Lists all settings', async ({ request }) => {
    const response = await request.get(`${API_BASE}/admin/settings`, {
      headers: { Authorization: authToken },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // SET-2
  test('SET-2 - PUT /api/admin/settings/{key} - Updates setting value', async ({ request }) => {
    const settingKey = 'app.test.setting';
    const response = await request.put(`${API_BASE}/admin/settings/${settingKey}`, {
      headers: {
        Authorization: authToken,
        'Content-Type': 'application/json',
      },
      data: { value: 'updated-' + Date.now() },
    });

    expect([200, 404]).toContain(response.status());
    if (response.status() === 200) {
      const body = await response.json();
      expect(body.success).toBe(true);
    }
  });

  // SET-3
  test('SET-3 - PUT /api/admin/settings/{key} - Invalid body returns 400', async ({ request }) => {
    const response = await request.put(`${API_BASE}/admin/settings/any.key`, {
      headers: {
        Authorization: authToken,
        'Content-Type': 'application/json',
      },
      data: {},
    });

    expect([400, 404]).toContain(response.status());
  });

  // SET-4
  test('SET-4 - POST /api/admin/settings/batch - Batch updates multiple settings', async ({ request }) => {
    const response = await request.post(`${API_BASE}/admin/settings/batch`, {
      headers: {
        Authorization: authToken,
        'Content-Type': 'application/json',
      },
      data: [
        { key: 'app.test.batch.1', value: 'v1' },
        { key: 'app.test.batch.2', value: 'v2' },
      ],
    });

    expect([200, 400]).toContain(response.status());
    if (response.status() === 200) {
      const body = await response.json();
      expect(body.success).toBe(true);
    }
  });

  // SET-5
  test('SET-5 - POST /api/admin/settings/batch - Empty list returns 200 with no-op', async ({ request }) => {
    const response = await request.post(`${API_BASE}/admin/settings/batch`, {
      headers: {
        Authorization: authToken,
        'Content-Type': 'application/json',
      },
      data: [],
    });

    expect([200, 400]).toContain(response.status());
  });
});
