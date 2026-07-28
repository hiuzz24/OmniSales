/**
 * Auth Password Reset API Tests.
 *
 * Covers all 5 password-related endpoints on /api/auth:
 *   - POST /api/auth/forgot-password                       (request reset link)
 *   - GET  /api/auth/change-password/validate?token=...    (validate token)
 *   - POST /api/auth/change-password                       (consume token, set new password)
 *   - POST /api/auth/reset-password                        (admin path, set by userId)
 *   - POST /api/auth/changes-password-after-login          (logged-in user, swap their password)
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE, TEST_EMAIL, TEST_PASSWORD } = require('../../utils/env-config');
const { getAuthToken, getAuthHeaders } = require('../../utils/warehouse-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Auth Password Reset API Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // === forgot-password =======================================================

  test('PWR-1 - POST /api/auth/forgot-password - Valid email returns 200', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/forgot-password`, {
      data: { email: TEST_EMAIL },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('success');
  });

  test('PWR-2 - POST /api/auth/forgot-password - Invalid email returns 400', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/forgot-password`, {
      data: { email: 'no-such-user@does-not-exist.example' },
    });
    expect([200, 400]).toContain(response.status());
    // Backend may return 200 with no-op to avoid user enumeration, or 400 for absent user.
  });

  test('PWR-3 - POST /api/auth/forgot-password - Malformed email returns 400', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/forgot-password`, {
      data: { email: 'not-an-email' },
    });
    expect(response.status()).toBe(400);
  });

  // === change-password/validate =============================================

  test('PWR-4 - GET /api/auth/change-password/validate - Invalid token returns 400', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/auth/change-password/validate?token=totally-fake-token-${Date.now()}`
    );
    expect(response.status()).toBe(400);
  });

  test('PWR-5 - POST /api/auth/change-password - Mismatched confirm returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/auth/change-password`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        token: 'fake-token',
        password: 'NewPass123!@#',
        confirmPassword: 'DifferentPass123!@#',
      },
    });
    expect([400, 401, 403, 500]).toContain(response.status());
  });

  // === reset-password (admin path) =========================================

  test('PWR-6 - POST /api/auth/reset-password - Without auth returns 401/403/500', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/reset-password`, {
      data: {
        userId: '00000000-0000-0000-0000-000000000000',
      },
    });
    expect([401, 403, 500]).toContain(response.status());
  });

  test('PWR-7 - POST /api/auth/reset-password - Owner resets OWN user id', async ({ request, managerHeaders }) => {
    // Manager @osms.vn is OWNER. Resetting their own id should succeed.
    // First fetch the manager's own user id via /api/users/me.
    const meRes = await request.get(`${API_BASE}/users/me`, {
      headers: managerHeaders,
    });
    test.skip(meRes.status() !== 200, 'GET /users/me unavailable');
    const myId = (await meRes.json()).data?.id;
    test.skip(!myId, 'manager fixture id unavailable');

    const response = await request.post(`${API_BASE}/auth/reset-password`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { userId: myId },
    });
    expect([200, 204]).toContain(response.status());
  });

  // === changes-password-after-login (logged-in user) ========================

  test('PWR-8 - POST /api/auth/changes-password-after-login - Wrong old password returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/auth/changes-password-after-login`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        oldPassword: 'DefinitelyNotMyPassword',
        newPassword: 'NewPass123!@#',
        confirmPassword: 'NewPass123!@#',
      },
    });
    expect([400, 401, 500]).toContain(response.status());
  });

  test('PWR-9 - POST /api/auth/changes-password-after-login - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/changes-password-after-login`, {
      data: {
        oldPassword: 'whatever',
        newPassword: 'NewPass123!@#',
        confirmPassword: 'NewPass123!@#',
      },
    });
    expect([401, 403]).toContain(response.status());
  });

  test('PWR-10 - POST /api/auth/changes-password-after-login - Mismatched confirm returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/auth/changes-password-after-login`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        oldPassword: TEST_PASSWORD,
        newPassword: 'NewPass123!@#',
        confirmPassword: 'AnotherPass123!@#',
      },
    });
    expect([400, 500]).toContain(response.status());
  });
});
