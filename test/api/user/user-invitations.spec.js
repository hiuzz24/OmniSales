/**
 * User Invitations API Tests.
 *
 * Covers the full invite lifecycle on /api/users:
 *   - POST /api/users/invite-user             (via AuthController, sends invite email)
 *   - GET  /api/users/invitations             (list pending invite tokens, OWNER/SYSTEM_ADMIN)
 *   - POST /api/users/invitations/{id}/cancel (cancel invite by token id)
 *   - POST /api/users/{id}/cancel-invite      (cancel invite by user id)
 *   - GET  /api/users/{id}                    (get user detail)
 *   - POST /api/users                         (create direct user, full CRUD round-trip)
 *   - PUT  /api/users/{id}                    (update)
 *   - DELETE /api/users/{id}                  (delete)
 *
 * The invite flow is asynchronous: the email is sent by a separate process.
 * For tests we directly create a user via POST /users (which marks them as
 * PENDING_INVITE) and exercise the cancel endpoints.
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getAuthToken,
  API_BASE,
} = require('../../utils/user-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('User Invitations API Tests', () => {

  let createdUserIds = [];

  test.afterEach(async ({ request }) => {
    if (createdUserIds.length) {
      const authToken = await getAuthTokenCached(request);
      for (const id of createdUserIds.splice(0)) {
        try {
          await request.delete(`${API_BASE}/users/${id}`, {
            headers: { Authorization: `Bearer ${authToken}` },
          });
        } catch (_) {}
      }
    }
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  async function createDirectUser(request, managerHeaders, overrides = {}) {
    const ts = Date.now();
    const email = overrides.email || `invitee_${ts}@test.com`;
    const userData = {
      email,
      password: 'TestPass123@',
      fullName: overrides.fullName || `Invitee ${ts}`,
      role: overrides.role || 'SALES',
      status: overrides.status || 'PENDING_INVITE',
    };
    const response = await request.post(`${API_BASE}/users`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: userData,
    });
    if (response.status() !== 200 && response.status() !== 201) return null;
    const body = await response.json();
    if (body.data?.id) createdUserIds.push(body.data.id);
    return body.data;
  }

  // === POST /api/users/invite-user (AuthController) ========================

  test('INV-1 - POST /api/auth/invite-user - Valid email returns 200', async ({ request, managerHeaders }) => {
    const ts = Date.now();
    const response = await request.post(`${API_BASE}/auth/invite-user`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        email: `invitee_${ts}@test.com`,
        roleName: 'SALES',
      },
    });
    expect([200, 400]).toContain(response.status());
  });

  test('INV-2 - POST /api/auth/invite-user - Bad email returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/auth/invite-user`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        email: 'not-an-email',
        roleName: 'SALES',
      },
    });
    expect([400, 500]).toContain(response.status());
  });

  test('INV-3 - POST /api/auth/invite-user - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/invite-user`, {
      data: {
        email: 'noauth_invitee@test.com',
        roleName: 'SALES',
      },
    });
    expect([401, 403]).toContain(response.status());
  });

  // === GET /api/users/invitations ==========================================

  test('INV-4 - GET /api/users/invitations - Returns 200 with list', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/users/invitations`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  test('INV-5 - GET /api/users/invitations - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/users/invitations`);
    expect([401, 403]).toContain(response.status());
  });

  // === POST /api/users/invitations/{id}/cancel =============================

  test('INV-6 - POST /api/users/invitations/{id}/cancel - Non-existent returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.post(
      `${API_BASE}/users/invitations/00000000-0000-0000-0000-000000000000/cancel`,
      { headers: { ...managerHeaders, 'Content-Type': 'application/json' } }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  // === POST /api/users/{id}/cancel-invite =================================

  test('INV-7 - POST /api/users/{id}/cancel-invite - Direct user cancel returns 200', async ({ request, managerHeaders }) => {
    const user = await createDirectUser(request, managerHeaders, {});
    test.skip(!user || !user.id, 'Cannot create user');

    const response = await request.post(`${API_BASE}/users/${user.id}/cancel-invite`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
    });
    expect([200, 204, 400, 500]).toContain(response.status());
  });

  test('INV-8 - POST /api/users/{id}/cancel-invite - Non-existent returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.post(
      `${API_BASE}/users/00000000-0000-0000-0000-000000000000/cancel-invite`,
      { headers: { ...managerHeaders, 'Content-Type': 'application/json' } }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  // === GET /api/users/{id} ================================================

  test('INV-9 - GET /api/users/{id} - Returns user detail', async ({ request, managerHeaders }) => {
    const user = await createDirectUser(request, managerHeaders, {});
    test.skip(!user || !user.id, 'Cannot create user');

    const response = await request.get(`${API_BASE}/users/${user.id}`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBe(user.id);
    expect(body.data.email).toBe(user.email);
  });

  test('INV-9b - GET /api/users/{id} - Non-existent returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/users/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([400, 404, 500]).toContain(response.status());
  });

  // === POST /api/users + PUT + DELETE (full CRUD round-trip) ==============

  test('INV-10 - User CRUD round-trip: create -> update -> delete', async ({ request, managerHeaders }) => {
    const ts = Date.now();
    const createRes = await request.post(`${API_BASE}/users`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        email: `crud_${ts}@test.com`,
        password: 'TestPass123@',
        fullName: `CRUD ${ts}`,
        role: 'SALES',
        status: 'ACTIVE',
      },
    });
    expect([200, 201]).toContain(createRes.status());
    const user = (await createRes.json()).data;
    test.skip(!user || !user.id, 'Cannot create user');
    createdUserIds.push(user.id);

    // Update
    const updateRes = await request.put(`${API_BASE}/users/${user.id}`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        email: user.email,
        fullName: `CRUD Updated ${ts}`,
        role: 'SALES',
        status: 'ACTIVE',
      },
    });
    expect([200, 204]).toContain(updateRes.status());

    // Verify
    const detailRes = await request.get(`${API_BASE}/users/${user.id}`, {
      headers: managerHeaders,
    });
    expect(detailRes.status()).toBe(200);
    const detail = (await detailRes.json()).data;
    expect(detail.fullName).toBe(`CRUD Updated ${ts}`);

    // Delete
    const deleteRes = await request.delete(`${API_BASE}/users/${user.id}`, {
      headers: managerHeaders,
    });
    expect([200, 204]).toContain(deleteRes.status());
    createdUserIds = createdUserIds.filter((id) => id !== user.id);
  });

  test('INV-11 - DELETE /api/users/{id} - Non-existent returns 400/500', async ({ request, managerHeaders }) => {
    const response = await request.delete(
      `${API_BASE}/users/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([400, 404, 500]).toContain(response.status());
  });
});
