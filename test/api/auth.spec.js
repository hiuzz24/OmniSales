const { test, expect } = require('@playwright/test');

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api';

test.describe('Auth API Tests', () => {

  test('POST /api/auth/login - Login successfully', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: {
        email: 'manager@osms.vn',
        password: 'Duy16042004%',
      },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('accessToken');
    expect(body.data).toHaveProperty('user');
    expect(body.data.user.email).toBe('manager@osms.vn');
  });

  test('POST /api/auth/login - Login with invalid credentials returns 401', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: {
        email: 'wrong@example.com',
        password: 'WrongPassword123@',
      },
    });

    expect(response.status()).toBe(401);
  });

  test('POST /api/auth/login - Login with missing email returns 400', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: {
        password: 'Password123@',
      },
    });

    expect(response.status()).toBe(400);
  });

  test('POST /api/auth/login - Login with invalid email format returns 400', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: {
        email: 'not-an-email',
        password: 'Password123@',
      },
    });

    expect(response.status()).toBe(400);
  });

  test('POST /api/auth/refresh - Returns 401 without refresh token', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/refresh`);

    expect(response.status()).toBe(401);
  });

  test('POST /api/auth/logout - Always returns 200 (refreshToken is optional cookie)', async ({ request }) => {
    const response = await request.post(`${API_BASE}/auth/logout`);

    expect(response.status()).toBe(200);
  });
});
