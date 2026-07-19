const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  createTestUser,
  cleanupTestUser,
  API_BASE,
} = require('../../utils/user-helpers');

test.describe('User API Tests', () => {

  let createdUserIds = [];

  test.afterEach(async ({ request, managerHeaders }) => {
    if (!createdUserIds.length) return;
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    for (const id of createdUserIds.splice(0)) {
      await cleanupTestUser(request, authToken, id);
    }
  });

  // POST /users
  test('USR-API-1 - POST /users - Create user returns 201', async ({ request, managerHeaders }) => {
    const userData = {
      email: `newuser_${Date.now()}@test.com`,
      password: 'NewPass123@',
      fullName: `New User ${Date.now()}`,
      role: 'SALES',
    };

    const response = await request.post(`${API_BASE}/users`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: userData,
    });

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('id');
    expect(body.data.email).toBe(userData.email);
    expect(body.data.fullName).toBe(userData.fullName);

    if (body.data.id) {
      createdUserIds.push(body.data.id);
    }
  });

  test('USR-API-2 - POST /users - Duplicate email returns 400/409', async ({ request, managerHeaders }) => {
    const email = `dup_${Date.now()}@test.com`;
    const userData = {
      email: email,
      password: 'TestPass123@',
      fullName: `Dup User ${Date.now()}`,
      role: 'SALES',
    };

    const createResponse = await request.post(`${API_BASE}/users`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: userData,
    });

    const dupResponse = await request.post(`${API_BASE}/users`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: userData,
    });

    expect(dupResponse.status()).toBeGreaterThanOrEqual(400);

    if (createResponse.ok()) {
      const body = await createResponse.json();
      if (body.data?.id) {
        createdUserIds.push(body.data.id);
      }
    }
  });

  test('USR-API-3 - POST /users - Without auth returns 401', async ({ request }) => {
    const response = await request.post(`${API_BASE}/users`, {
      data: {
        email: `auth_test_${Date.now()}@test.com`,
        password: 'TestPass123@',
        fullName: 'Auth Test',
        role: 'SALES',
      },
    });

    expect([401, 403]).toContain(response.status());
  });

  // GET /users
  test('USR-API-4 - GET /users - List paginated returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/users?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('USR-API-5 - GET /users - Without auth returns 401', async ({ request }) => {
    const response = await request.get(`${API_BASE}/users?page=0&size=5`);
    expect([401, 403]).toContain(response.status());
  });

  // GET /users/{id}
  test('USR-API-6 - GET /users/{id} - Get user by valid ID returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const user = await createTestUser(request, authToken, {
      email: `getbyid_${Date.now()}@test.com`,
      fullName: 'Get By ID Test',
    });

    test.skip(!user?.id, 'Cannot create test user');

    const response = await request.get(`${API_BASE}/users/${user.id}`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBe(user.id);

    createdUserIds.push(user.id);
  });

  test('USR-API-7 - GET /users/{id} - Non-existent ID returns 404', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';

    const response = await request.get(`${API_BASE}/users/${fakeId}`, {
      headers: managerHeaders,
    });

    expect([404]).toContain(response.status());
  });

  // PUT /users/{id}
  test('USR-API-8 - PUT /users/{id} - Update user returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const user = await createTestUser(request, authToken, {
      email: `update_${Date.now()}@test.com`,
      fullName: 'Before Update',
    });

    test.skip(!user?.id, 'Cannot create test user');

    const updateData = {
      email: user.email,
      fullName: 'After Update',
      role: 'OPERATIONS',
    };

    const updateResponse = await request.put(`${API_BASE}/users/${user.id}`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: updateData,
    });

    expect(updateResponse.status()).toBe(200);
    const body = await updateResponse.json();
    expect(body.data.fullName).toBe('After Update');

    createdUserIds.push(user.id);
  });

  // DELETE /users/{id}
  test('USR-API-9 - DELETE /users/{id} - Soft delete returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const user = await createTestUser(request, authToken, {
      email: `delete_${Date.now()}@test.com`,
      fullName: 'Delete Me',
    });

    test.skip(!user?.id, 'Cannot create test user');

    const deleteResponse = await request.delete(`${API_BASE}/users/${user.id}`, {
      headers: managerHeaders,
    });

    expect([200, 204]).toContain(deleteResponse.status());
  });

  // GET /users/me
  test('USR-API-10 - GET /users/me - Get current profile returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/users/me`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('email');
    expect(body.data).toHaveProperty('fullName');
  });

  // PUT /users/me
  test('USR-API-11 - PUT /users/me - Update own profile returns 200', async ({ request, managerHeaders }) => {
    const originalResponse = await request.get(`${API_BASE}/users/me`, {
      headers: managerHeaders,
    });
    const originalBody = await originalResponse.json();
    const originalName = originalBody.data?.fullName || 'Original';

    const updateData = { fullName: `Updated ${Date.now()}` };

    const response = await request.put(`${API_BASE}/users/me`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: updateData,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.data.fullName).toBe(updateData.fullName);

    await request.put(`${API_BASE}/users/me`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { fullName: originalName },
    });
  });
});
