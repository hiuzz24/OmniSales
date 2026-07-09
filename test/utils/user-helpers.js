const wh = require('./warehouse-helpers');

const { TEST_EMAIL, TEST_PASSWORD, API_BASE } = require('./env-config');

async function getAuthTokenWithRetry(request, retries = 5) {
  for (let i = 0; i < retries; i++) {
    const response = await request.post(`${API_BASE}/auth/login`, {
      data: { email: TEST_EMAIL, password: TEST_PASSWORD },
    });
    if (response.status() === 200) {
      const body = await response.json();
      return body.data.accessToken;
    }
    if (response.status() === 429 || response.status() === 401 || response.status() === 403) {
      await new Promise(r => setTimeout(r, 3000 * (i + 1)));
      continue;
    }
    throw new Error(`Login failed with status ${response.status()}`);
  }
  throw new Error('Login failed after retries');
}

async function getUserId(request, token) {
  const response = await request.get(`${API_BASE}/users?page=0&size=1`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (response.status() === 200) {
    const body = await response.json();
    const data = body.data?.content || body.data || [];
    if (data.length > 0) return data[0].id;
  }
  return null;
}

async function getUsers(request, token) {
  const response = await request.get(`${API_BASE}/users?page=0&size=50`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (response.status() === 200) {
    const body = await response.json();
    return body.data?.content || body.data || [];
  }
  return [];
}

async function createTestUser(request, token, overrides = {}) {
  const timestamp = Date.now();
  const email = overrides.email || `testuser_${timestamp}@test.com`;
  const userData = {
    email: email,
    password: overrides.password || 'TestPass123@',
    fullName: overrides.fullName || `Test User ${timestamp}`,
    phone: overrides.phone || null,
    role: overrides.role || 'SALES',
    status: overrides.status || 'ACTIVE',
  };

  const response = await request.post(`${API_BASE}/users`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: userData,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    return null;
  }

  const body = await response.json();
  return body.data;
}

async function cleanupTestUser(request, token, userId) {
  if (!userId) return;
  try {
    await request.delete(`${API_BASE}/users/${userId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (e) {
  }
}

function uniqueCode(prefix = 'TEST') {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 99999)}`;
}

module.exports = {
  ...wh,
  getAuthToken: getAuthTokenWithRetry,
  getUserId,
  getUsers,
  createTestUser,
  cleanupTestUser,
  uniqueCode,
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
};
