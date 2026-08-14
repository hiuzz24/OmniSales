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
 *
 * Backend no longer exposes a public POST /api/orders endpoint after the
 * manual-order refactor (it returns 405).  When the POST returns 405
 * we fall back to a direct SQL insert so that downstream tests which
 * need a real order (cancel, payment-status, etc.) can still run.
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

  if (response.status() === 201 || response.status() === 200) {
    const body = await response.json();
    return body.data;
  }

  // 405 = backend no longer exposes POST /api/orders (manual refactor).
  // Fall back to direct SQL insert so consumers still get an order.
  if (response.status() === 405) {
    return await createTestOrderViaSql(overrides);
  }

  throw new Error(`Create order failed with status ${response.status()}`);
}

async function createTestOrderViaSql(overrides = {}) {
  const { Client } = require('pg');
  const client = new Client({
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT) || 5432,
    user: process.env.DB_USERNAME || 'postgres',
    password: process.env.DB_PASSWORD || '123',
    database: process.env.DB_NAME || 'OSMS',
  });
  await client.connect();
  try {
    const ts = Date.now();
    const externalOrderId = overrides.externalOrderId
      || `TEST-ORD-${ts}-${Math.random().toString(36).slice(2, 8)}`;
    const note = overrides.note || `Test order note ${ts}`;
    const subtotal = overrides.subtotal !== undefined ? overrides.subtotal : 150000;
    const discountAmount = overrides.discountAmount !== undefined ? overrides.discountAmount : 0;
    const shippingFee = overrides.shippingFee !== undefined ? overrides.shippingFee : 0;
    const status = overrides.status || 'PENDING';
    const paymentStatus = overrides.paymentStatus || 'UNPAID';
    const platform = overrides.platform || 'MANUAL';
    const channelName = overrides.channelName || `Test Channel ${ts}`;
    const buyerName = overrides.buyerName || 'Test Customer';
    const buyerPhone = overrides.buyerPhone || '0912345678';
    const shippingAddress = overrides.shippingAddress || {
      fullName: buyerName, phone: buyerPhone, address: '123 Test Street',
      city: 'Ho Chi Minh City', district: 'District 1', ward: 'Ward 1',
    };

    const order = await client.query(
      `INSERT INTO orders
        (id, customer_id, channel_id, platform, channel_name, external_order_id,
         status, payment_status, buyer_name, buyer_phone, shipping_address,
         subtotal, discount_amount, shipping_fee, currency, note,
         created_at, updated_at)
       VALUES (gen_random_uuid(), $1, $2, $3, $4, $5,
               $6::order_status, $7, $8, $9, $10::jsonb,
               $11::numeric, $12::numeric, $13::numeric, 'VND', $14,
               NOW(), NOW())
       RETURNING id, external_order_id, status, payment_status, subtotal,
                 discount_amount, shipping_fee, buyer_name, buyer_phone, note`,
      [
        overrides.customerId || null,
        overrides.channelId || null,
        platform,
        channelName,
        externalOrderId,
        status,
        paymentStatus,
        buyerName,
        buyerPhone,
        JSON.stringify(shippingAddress),
        String(subtotal),
        String(discountAmount),
        String(shippingFee),
        note,
      ]
    );

    const insertedOrder = order.rows[0];

    const items = overrides.items || [
      {
        sku: externalOrderId,
        name: `Test Order Item ${ts}`,
        quantity: 1,
        unitPrice: subtotal,
        discountAmount: 0,
      },
    ];

    for (const it of items) {
      await client.query(
        `INSERT INTO order_items
          (id, order_id, sku, name, quantity, unit_price, discount_amount)
         VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6)`,
        [insertedOrder.id, it.sku, it.name, it.quantity, it.unitPrice, it.discountAmount || 0]
      );
    }

    return {
      id: insertedOrder.id,
      externalOrderId: insertedOrder.external_order_id,
      status: insertedOrder.status,
      paymentStatus: insertedOrder.payment_status,
      subtotal: Number(insertedOrder.subtotal),
      discountAmount: Number(insertedOrder.discount_amount),
      shippingFee: Number(insertedOrder.shipping_fee),
      buyerName: insertedOrder.buyer_name,
      buyerPhone: insertedOrder.buyer_phone,
      note: insertedOrder.note,
    };
  } finally {
    await client.end();
  }
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
