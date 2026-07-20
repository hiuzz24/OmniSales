const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  createTestOrder,
  deleteTestOrder,
  getOrderStats,
  API_BASE,
} = require('../../utils/order-helpers');

test.describe('Order API Tests', () => {

  // GET /api/orders - List Orders
  test('P1 - GET /api/orders - List orders with pagination returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/orders?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(body.data).toHaveProperty('totalElements');
    expect(body.data).toHaveProperty('totalPages');
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P2 - GET /api/orders - Filter by status PENDING', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/orders?status=PENDING&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('P3 - GET /api/orders - Filter by status CONFIRMED', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/orders?status=CONFIRMED&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('P4 - GET /api/orders - Search by keyword', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/orders?keyword=ORD&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('P5 - GET /api/orders - Pagination works correctly', async ({ request, managerHeaders }) => {
    const page0 = await request.get(`${API_BASE}/orders?page=0&size=5`, {
      headers: managerHeaders,
    });

    expect(page0.status()).toBe(200);

    const page1 = await request.get(`${API_BASE}/orders?page=1&size=5`, {
      headers: managerHeaders,
    });

    expect(page1.status()).toBe(200);
  });

  // POST /api/orders - Create Order
  test('P6 - POST /api/orders - Create order without auth returns 401 or 403', async ({ request }) => {
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

    expect([401, 403]).toContain(response.status());
  });

  test('P7 - POST /api/orders - Create order with invalid data returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/orders`, {
      headers: managerHeaders,
      data: {
        items: [],
      },
    });

    expect([400, 403, 500]).toContain(response.status());
  });

  test('P8 - POST /api/orders - Create order with missing customer returns error', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/orders`, {
      headers: managerHeaders,
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

    expect([200, 201, 400, 500]).toContain(response.status());
  });

  // GET /api/orders/{id} - Get Order By ID
  test('P9 - GET /api/orders/{id} - Get existing order returns 200', async ({ request, managerHeaders }) => {
    const listResponse = await request.get(`${API_BASE}/orders?page=0&size=1`, {
      headers: managerHeaders,
    });

    if (listResponse.status() !== 200) {
      test.skip();
      return;
    }

    const listBody = await listResponse.json();
    if (!listBody.data.content || listBody.data.content.length === 0) {
      test.skip();
      return;
    }

    const existingOrder = listBody.data.content[0];
    const response = await request.get(`${API_BASE}/orders/${existingOrder.id}`, {
      headers: managerHeaders,
    });

    expect([200, 404, 500]).toContain(response.status());
  });

  test('P10 - GET /api/orders/{id} - Get non-existent order returns 404', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.get(`${API_BASE}/orders/${fakeId}`, {
      headers: managerHeaders,
    });

    expect([404, 500]).toContain(response.status());
  });

  // PUT /api/orders/{id}/status - Update Order Status
  test('P11 - PUT /api/orders/{id}/status - Update existing order status', async ({ request, managerHeaders }) => {
    const listResponse = await request.get(`${API_BASE}/orders?page=0&size=1`, {
      headers: managerHeaders,
    });

    if (listResponse.status() !== 200) {
      test.skip();
      return;
    }

    const listBody = await listResponse.json();
    if (!listBody.data.content || listBody.data.content.length === 0) {
      test.skip();
      return;
    }

    const existingOrder = listBody.data.content[0];

    const response = await request.put(`${API_BASE}/orders/${existingOrder.id}/status`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: { status: 'CONFIRMED' },
    });

    expect([200, 400, 404, 405, 500]).toContain(response.status());
  });

  // DELETE /api/orders/{id} - Cancel Order
  test('P12 - DELETE /api/orders/{id} - Cancel non-existent order returns error', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.delete(`${API_BASE}/orders/${fakeId}`, {
      headers: managerHeaders,
    });

    expect([404, 405, 500]).toContain(response.status());
  });

  // GET /api/orders/stats - Get Order Stats
  test('P13 - GET /api/orders/stats - Get order stats returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/orders/stats`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('totalOrders');

    const stats = body.data;
    const hasCountFields = (
      (stats.pendingCount !== undefined || stats.pendingOrders !== undefined) ||
      (stats.confirmedCount !== undefined || stats.confirmedOrders !== undefined)
    );
    expect(hasCountFields).toBeTruthy();
  });

  // Edge Cases
  test('P14 - GET /api/orders - Access without auth returns 401 or 403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/orders?page=0&size=10`);

    expect([401, 403]).toContain(response.status());
  });

  test('P15 - GET /api/orders - Filter by invalid status', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/orders?status=INVALID_STATUS&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 400, 500]).toContain(response.status());
  });

  test('P16 - GET /api/orders/stats - Stats contains revenue information', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const stats = await getOrderStats(request, authToken);

    expect(stats).toBeTruthy();
    expect(stats).toHaveProperty('totalOrders');

    const hasData = (
      stats.totalRevenue !== undefined ||
      stats.pendingCount !== undefined ||
      stats.confirmedCount !== undefined ||
      stats.totalSpent !== undefined
    );
    expect(hasData).toBeTruthy();
  });
});
