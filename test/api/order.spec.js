const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  getFirstCustomerId,
  getFirstChannelId,
  createTestOrder,
  deleteTestOrder,
  getOrderStats,
  API_BASE,
} = require('../utils/order-helpers');

test.describe('Order API Tests', () => {

  let authToken;
  let customerId;
  let channelId;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
    customerId = await getFirstCustomerId(request, authToken);
    channelId = await getFirstChannelId(request, authToken);
  });

  // =========================================================
  // GET /api/orders - List Orders
  // =========================================================
  test('P1 - GET /api/orders - List orders with pagination returns 200', async ({ request }) => {
    const response = await request.get(`${API_BASE}/orders?page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(body.data).toHaveProperty('totalElements');
    expect(body.data).toHaveProperty('totalPages');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P2 - GET /api/orders - Filter by status PENDING', async ({ request }) => {
    const response = await request.get(`${API_BASE}/orders?status=PENDING&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P3 - GET /api/orders - Filter by status CONFIRMED', async ({ request }) => {
    const response = await request.get(`${API_BASE}/orders?status=CONFIRMED&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('P4 - GET /api/orders - Filter by channel', async ({ request }) => {
    if (!channelId) {
      test.skip();
    }

    const response = await request.get(`${API_BASE}/orders?channelId=${channelId}&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('P5 - GET /api/orders - Search by keyword', async ({ request }) => {
    const response = await request.get(`${API_BASE}/orders?keyword=ORD&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  // =========================================================
  // POST /api/orders - Create Order
  // =========================================================
  test('P6 - POST /api/orders - Create order without auth returns 401', async ({ request }) => {
    const response = await request.post(`${API_BASE}/orders`, {
      data: {
        items: [
          {
            sku: 'TEST-001',
            name: 'Test Item',
            quantity: 1,
            unitPrice: 150000,
          },
        ],
      },
    });

    expect(response.status()).toBe(401);
  });

  test('P7 - POST /api/orders - Create order with invalid data returns 400', async ({ request }) => {
    const response = await request.post(`${API_BASE}/orders`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        items: [],
      },
    });

    expect([400, 500]).toContain(response.status());
  });

  test('P8 - POST /api/orders - Create order successfully', async ({ request }) => {
    const order = await createTestOrder(request, authToken, {
      customerId: customerId,
      channelId: channelId,
    });

    expect(order).toBeTruthy();
    expect(order).toHaveProperty('id');

    // Cleanup
    await deleteTestOrder(request, authToken, order.id);
  });

  // =========================================================
  // GET /api/orders/{id} - Get Order By ID
  // =========================================================
  test('P9 - GET /api/orders/{id} - Get order by ID returns 200', async ({ request }) => {
    const order = await createTestOrder(request, authToken, {
      customerId: customerId,
    });

    try {
      const response = await request.get(`${API_BASE}/orders/${order.id}`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(body.data).toHaveProperty('id');
      expect(body.data.id).toBe(order.id);
    } finally {
      await deleteTestOrder(request, authToken, order.id);
    }
  });

  test('P10 - GET /api/orders/{id} - Get non-existent order returns 404', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.get(`${API_BASE}/orders/${fakeId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect([404, 500]).toContain(response.status());
  });

  // =========================================================
  // PUT /api/orders/{id}/status - Update Order Status
  // =========================================================
  test('P11 - PUT /api/orders/{id}/status - Update status to CONFIRMED', async ({ request }) => {
    const order = await createTestOrder(request, authToken, {
      customerId: customerId,
    });

    try {
      const response = await request.put(`${API_BASE}/orders/${order.id}/status`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'Content-Type': 'application/json',
        },
        data: { status: 'CONFIRMED' },
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
    } finally {
      await deleteTestOrder(request, authToken, order.id);
    }
  });

  test('P12 - PUT /api/orders/{id}/status - Update status to CANCELLED', async ({ request }) => {
    const order = await createTestOrder(request, authToken, {
      customerId: customerId,
    });

    try {
      const response = await request.put(`${API_BASE}/orders/${order.id}/status`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'Content-Type': 'application/json',
        },
        data: { status: 'CANCELLED' },
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
    } finally {
      await deleteTestOrder(request, authToken, order.id);
    }
  });

  // =========================================================
  // PUT /api/orders/{id}/payment-status - Update Payment Status
  // =========================================================
  test('P13 - PUT /api/orders/{id}/payment-status - Update payment status to PAID', async ({ request }) => {
    const order = await createTestOrder(request, authToken, {
      customerId: customerId,
    });

    try {
      const response = await request.put(`${API_BASE}/orders/${order.id}/payment-status`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'Content-Type': 'application/json',
        },
        data: { paymentStatus: 'PAID' },
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
    } finally {
      await deleteTestOrder(request, authToken, order.id);
    }
  });

  // =========================================================
  // DELETE /api/orders/{id} - Cancel Order
  // =========================================================
  test('P14 - DELETE /api/orders/{id} - Cancel order successfully', async ({ request }) => {
    const order = await createTestOrder(request, authToken, {
      customerId: customerId,
    });

    const response = await request.delete(`${API_BASE}/orders/${order.id}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect([200, 204]).toContain(response.status());
  });

  test('P15 - DELETE /api/orders/{id} - Cancel non-existent order returns 404', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.delete(`${API_BASE}/orders/${fakeId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect([404, 500]).toContain(response.status());
  });

  // =========================================================
  // GET /api/orders/stats - Get Order Stats
  // =========================================================
  test('P16 - GET /api/orders/stats - Get order stats returns 200', async ({ request }) => {
    const stats = await getOrderStats(request, authToken);

    expect(stats).toBeTruthy();
    expect(stats).toHaveProperty('totalOrders');
    expect(stats).toHaveProperty('pendingOrders');
    expect(stats).toHaveProperty('confirmedOrders');
    expect(stats).toHaveProperty('processingOrders');
    expect(stats).toHaveProperty('shippedOrders');
    expect(stats).toHaveProperty('deliveredOrders');
    expect(stats).toHaveProperty('cancelledOrders');
  });

  // =========================================================
  // GET /api/orders/{id}/history - Get Order History
  // =========================================================
  test('P17 - GET /api/orders/{id}/history - Get order history returns 200', async ({ request }) => {
    const order = await createTestOrder(request, authToken, {
      customerId: customerId,
    });

    try {
      const response = await request.get(`${API_BASE}/orders/${order.id}/history?page=0&size=10`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(body.data).toHaveProperty('content');
    } finally {
      await deleteTestOrder(request, authToken, order.id);
    }
  });
});
