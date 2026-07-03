/**
 * Helper utilities for customer E2E and API tests
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api';
const TEST_EMAIL = 'manager@osms.vn';
const TEST_PASSWORD = 'Duy16042004%';

/**
 * Login via API and return access token
 */
async function getAuthToken(request) {
  const response = await request.post(`${API_BASE}/auth/login`, {
    data: { email: TEST_EMAIL, password: TEST_PASSWORD },
  });

  if (response.status() !== 200) {
    throw new Error(`Login failed with status ${response.status()}`);
  }

  const body = await response.json();
  return body.data.accessToken;
}

/**
 * Get a valid auth header for API calls
 */
async function getAuthHeaders(request) {
  const token = await getAuthToken(request);
  return { Authorization: `Bearer ${token}` };
}

/**
 * Create a test customer via API
 * Returns the created customer object with id
 */
async function createTestCustomer(request, token, overrides = {}) {
  const timestamp = Date.now();

  const defaultCustomer = {
    fullName: `Test Customer ${timestamp}`,
    email: `test${timestamp}@example.com`,
    phone: `09${String(timestamp).slice(-8)}`,
    gender: overrides.gender || 'Nam',
    dateOfBirth: '1990-01-01',
    address: `Address ${timestamp}`,
    city: 'Ho Chi Minh City',
    isActive: true,
  };

  const response = await request.post(`${API_BASE}/customers`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: defaultCustomer,
  });

  if (response.status() !== 201 && response.status() !== 200) {
    throw new Error(`Create customer failed with status ${response.status()}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Delete test customer
 */
async function deleteTestCustomer(request, token, customerId) {
  try {
    await request.delete(`${API_BASE}/customers/${customerId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (e) {
    // Ignore errors during cleanup
  }
}

/**
 * Get customer by ID
 */
async function getCustomerById(request, token, customerId) {
  const response = await request.get(`${API_BASE}/customers/${customerId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() !== 200) {
    return null;
  }

  const body = await response.json();
  return body.data;
}

/**
 * Update customer
 */
async function updateTestCustomer(request, token, customerId, updates) {
  const response = await request.put(`${API_BASE}/customers/${customerId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: updates,
  });

  if (response.status() !== 200) {
    throw new Error(`Update customer failed with status ${response.status()}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Get customer stats
 */
async function getCustomerStats(request, token) {
  const response = await request.get(`${API_BASE}/customers/stats`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() !== 200) {
    return null;
  }

  const body = await response.json();
  return body.data;
}

module.exports = {
  API_BASE,
  TEST_EMAIL,
  TEST_PASSWORD,
  getAuthToken,
  getAuthHeaders,
  createTestCustomer,
  deleteTestCustomer,
  getCustomerById,
  updateTestCustomer,
  getCustomerStats,
};
