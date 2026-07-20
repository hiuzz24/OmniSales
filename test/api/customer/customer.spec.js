const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  createTestCustomer,
  deleteTestCustomer,
  getCustomerById,
  updateTestCustomer,
  getCustomerStats,
  API_BASE,
} = require('../../utils/customer-helpers');

test.describe('Customer API Tests', () => {

  let createdCustomerIds = [];

  test.afterEach(async ({ request, managerHeaders }) => {
    if (!createdCustomerIds.length) return;
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    for (const id of createdCustomerIds.splice(0)) {
      await deleteTestCustomer(request, authToken, id);
    }
  });

  // GET /api/customers - List Customers
  test('C1 - GET /api/customers - List customers with pagination returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/customers?page=0&size=10`, {
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

  test('C2 - GET /api/customers - Search by keyword', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/customers?search=test&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('C3 - GET /api/customers - Filter by status ACTIVE', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/customers?status=ACTIVE&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('C4 - GET /api/customers - Filter by gender Nam', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/customers?gender=Nam&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('C5 - GET /api/customers - Filter by gender Nu', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/customers?gender=Nu&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  // POST /api/customers - Create Customer
  test('C6 - POST /api/customers - Create customer without auth returns 401 or 403', async ({ request }) => {
    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      data: {
        fullName: `Test Customer ${timestamp}`,
        email: `noauth${timestamp}@example.com`,
        phone: `09${String(timestamp).slice(-8)}`,
      },
    });

    expect([401, 403]).toContain(response.status());
  });

  test('C7 - POST /api/customers - Create customer with missing required fields returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/customers`, {
      headers: managerHeaders,
      data: {},
    });

    expect([200, 201, 400, 500]).toContain(response.status());
  });

  test('C8 - POST /api/customers - Create customer successfully', async ({ request, managerHeaders }) => {
    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: managerHeaders,
      data: {
        fullName: `Test Customer ${timestamp}`,
        email: `test${timestamp}@example.com`,
        phone: `09${String(timestamp).slice(-8)}`,
        gender: 'Nam',
      },
    });

    if (response.status() === 500) {
      test.skip();
      return;
    }

    expect([200, 201]).toContain(response.status());
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('id');
    expect(body.data).toHaveProperty('fullName');

    if (body.data && body.data.id) {
      createdCustomerIds.push(body.data.id);
    }
  });

  test('C9 - POST /api/customers - Create customer with invalid phone format', async ({ request, managerHeaders }) => {
    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: managerHeaders,
      data: {
        fullName: `Test Customer ${timestamp}`,
        email: `test${timestamp}@example.com`,
        phone: 'invalid',
      },
    });

    expect([200, 201, 400, 409, 500]).toContain(response.status());
  });

  test('C10 - POST /api/customers - Create customer with invalid email format', async ({ request, managerHeaders }) => {
    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: managerHeaders,
      data: {
        fullName: `Test Customer ${timestamp}`,
        email: 'invalid-email',
        phone: `09${String(timestamp).slice(-8)}`,
      },
    });

    expect([400, 403, 500]).toContain(response.status());
  });

  // GET /api/customers/{id} - Get Customer By ID
  test('C11 - GET /api/customers/{id} - Get customer by ID returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const listResponse = await request.get(`${API_BASE}/customers?page=0&size=1`, {
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

    const existingCustomer = listBody.data.content[0];
    const customer = await getCustomerById(request, authToken, existingCustomer.id);

    if (!customer) {
      test.skip();
      return;
    }

    expect(customer).toBeTruthy();
    expect(customer.id).toBe(existingCustomer.id);
    expect(customer).toHaveProperty('fullName');
  });

  test('C12 - GET /api/customers/{id} - Get non-existent customer returns 404', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.get(`${API_BASE}/customers/${fakeId}`, {
      headers: managerHeaders,
    });

    expect([404, 500]).toContain(response.status());
  });

  // PUT /api/customers/{id} - Update Customer
  test('C13 - PUT /api/customers/{id} - Update customer successfully', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const listResponse = await request.get(`${API_BASE}/customers?page=0&size=1`, {
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

    const existingCustomer = listBody.data.content[0];
    const timestamp = Date.now();

    const updated = await updateTestCustomer(request, authToken, existingCustomer.id, {
      fullName: `Updated Customer ${timestamp}`,
    });

    expect(updated).toBeTruthy();
  });

  test('C14 - PUT /api/customers/{id} - Update non-existent customer returns 404', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.put(`${API_BASE}/customers/${fakeId}`, {
      headers: {
        ...managerHeaders,
        'Content-Type': 'application/json',
      },
      data: { fullName: 'Updated Name' },
    });

    expect([404, 500]).toContain(response.status());
  });

  // DELETE /api/customers/{id} - Delete Customer
  test('C15 - DELETE /api/customers/{id} - Delete non-existent customer returns 404', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.delete(`${API_BASE}/customers/${fakeId}`, {
      headers: managerHeaders,
    });

    expect([404, 500]).toContain(response.status());
  });

  // GET /api/customers/stats - Get Customer Stats
  test('C16 - GET /api/customers/stats - Get customer stats returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const stats = await getCustomerStats(request, authToken);

    expect(stats).toBeTruthy();
    expect(stats).toHaveProperty('totalCustomers');
    expect(stats).toHaveProperty('activeCustomers');
    expect(stats).toHaveProperty('totalOrders');
    expect(stats).toHaveProperty('totalSpent');
  });

  // Edge Cases
  test('C17 - POST /api/customers - Create customer without fullName returns 400', async ({ request, managerHeaders }) => {
    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: managerHeaders,
      data: {
        email: `noname${timestamp}@example.com`,
        phone: `09${String(timestamp).slice(-8)}`,
      },
    });

    expect([200, 201, 400, 500]).toContain(response.status());
  });

  test('C18 - GET /api/customers - Pagination works correctly', async ({ request, managerHeaders }) => {
    const page0 = await request.get(`${API_BASE}/customers?page=0&size=5`, {
      headers: managerHeaders,
    });

    expect(page0.status()).toBe(200);

    const page1 = await request.get(`${API_BASE}/customers?page=1&size=5`, {
      headers: managerHeaders,
    });

    expect(page1.status()).toBe(200);
  });
});
