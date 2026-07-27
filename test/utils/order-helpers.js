/**
 * Helper utilities for order E2E and API tests
 */

const {
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE: ENV_API_BASE,
} = require('./env-config');
const API_BASE = process.env.API_BASE || ENV_API_BASE;

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
    platform: overrides.platform || 'MANUAL',
    channelName: overrides.channelName || `Test Channel ${timestamp}`,
    externalOrderId: overrides.externalOrderId || `TEST-ORD-${timestamp}`,
    status: overrides.status || 'PENDING',
    paymentStatus: overrides.paymentStatus || 'UNPAID',
    buyerName: overrides.buyerName || 'Test Customer',
    buyerPhone: overrides.buyerPhone || '0912345678',
    shippingAddress: overrides.shippingAddress || {
      fullName: 'Test Customer',
      phone: '0912345678',
      address: '123 Test Street',
      city: 'Ho Chi Minh City',
      district: 'District 1',
      ward: 'Ward 1',
    },
    subtotal: overrides.subtotal !== undefined ? overrides.subtotal : 150000,
    discountAmount: overrides.discountAmount !== undefined ? overrides.discountAmount : 0,
    shippingFee: overrides.shippingFee !== undefined ? overrides.shippingFee : 0,
    currency: overrides.currency || 'VND',
    note: overrides.note || `Test order note ${timestamp}`,
    items: overrides.items || [
      {
        sku: `TEST-ORD-${timestamp}`,
        name: `Test Order Item ${timestamp}`,
        quantity: 1,
        unitPrice: 150000,
        discountAmount: 0,
      },
    ],
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
 * Cancel a test order. The backend does not expose DELETE /api/orders/{id};
 * the canonical cleanup path is POST /api/orders/{id}/cancel.
 */
async function deleteTestOrder(request, token, orderId) {
  if (!orderId) return;
  try {
    const resp = await request.post(`${API_BASE}/orders/${orderId}/cancel`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });
    // Fallback: try old DELETE endpoint in case backend implements it.
    if (resp.status() === 404 || resp.status() === 405) {
      await request.delete(`${API_BASE}/orders/${orderId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
    }
  } catch (e) {
    // best-effort
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
