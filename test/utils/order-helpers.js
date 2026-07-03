/**
 * Helper utilities for order E2E and API tests
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
 * Create a test order via API
 * Returns the created order object with id
 */
async function createTestOrder(request, token, overrides = {}) {
  const timestamp = Date.now();

  const defaultOrder = {
    customerId: overrides.customerId || null,
    channelId: overrides.channelId || null,
    items: overrides.items || [
      {
        variantId: overrides.variantId || null,
        sku: `TEST-ORD-${timestamp}`,
        name: `Test Order Item ${timestamp}`,
        quantity: 1,
        unitPrice: 150000,
        discountAmount: 0,
      },
    ],
    shippingAddress: overrides.shippingAddress || {
      fullName: 'Test Customer',
      phone: '0912345678',
      address: '123 Test Street',
      city: 'Ho Chi Minh City',
      district: 'District 1',
      ward: 'Ward 1',
    },
    note: `Test order note ${timestamp}`,
  };

  const response = await request.post(`${API_BASE}/orders`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: defaultOrder,
  });

  if (response.status() !== 201 && response.status() !== 200) {
    throw new Error(`Create order failed with status ${response.status()}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Delete test order (cancel it)
 */
async function deleteTestOrder(request, token, orderId) {
  try {
    await request.delete(`${API_BASE}/orders/${orderId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (e) {
    // Ignore errors during cleanup
  }
}

/**
 * Get a customer ID for creating orders
 */
async function getFirstCustomerId(request, token) {
  const response = await request.get(`${API_BASE}/customers?page=0&size=1`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() !== 200) {
    return null;
  }

  const body = await response.json();
  if (body.data && body.data.content && body.data.content.length > 0) {
    return body.data.content[0].id;
  }
  return null;
}

/**
 * Get a channel ID for creating orders
 */
async function getFirstChannelId(request, token) {
  const response = await request.get(`${API_BASE}/channels?page=0&size=1`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() !== 200) {
    return null;
  }

  const body = await response.json();
  if (body.data && body.data.content && body.data.content.length > 0) {
    return body.data.content[0].id;
  }
  return null;
}

/**
 * Get order stats
 */
async function getOrderStats(request, token) {
  const response = await request.get(`${API_BASE}/orders/stats`, {
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
  createTestOrder,
  deleteTestOrder,
  getFirstCustomerId,
  getFirstChannelId,
  getOrderStats,
};
