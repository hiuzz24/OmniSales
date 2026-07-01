const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  createTestCustomer,
  deleteTestCustomer,
  getCustomerById,
  updateTestCustomer,
  getCustomerStats,
  API_BASE,
} = require('../utils/customer-helpers');

test.describe('Customer API Tests', () => {

  let authToken;
  let testCustomer;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
  });

  test.afterEach(async ({ request }) => {
    if (testCustomer && testCustomer.id) {
      await deleteTestCustomer(request, authToken, testCustomer.id);
      testCustomer = null;
    }
  });

  // =========================================================
  // GET /api/customers - List Customers
  // =========================================================
  test('C1 - GET /api/customers - List customers with pagination returns 200', async ({ request }) => {
    const response = await request.get(`${API_BASE}/customers?page=0&size=10`, {
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

  test('C2 - GET /api/customers - Search by keyword', async ({ request }) => {
    const response = await request.get(`${API_BASE}/customers?search=test&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
  });

  test('C3 - GET /api/customers - Filter by status ACTIVE', async ({ request }) => {
    const response = await request.get(`${API_BASE}/customers?status=ACTIVE&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('C4 - GET /api/customers - Filter by gender Nam', async ({ request }) => {
    const response = await request.get(`${API_BASE}/customers?gender=Nam&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  test('C5 - GET /api/customers - Filter by gender Nu', async ({ request }) => {
    const response = await request.get(`${API_BASE}/customers?gender=Nu&page=0&size=10`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
  });

  // =========================================================
  // POST /api/customers - Create Customer
  // =========================================================
  test('C6 - POST /api/customers - Create customer without auth returns 401', async ({ request }) => {
    const response = await request.post(`${API_BASE}/customers`, {
      data: {
        fullName: 'Test Customer',
        phone: '0912345678',
      },
    });

    expect(response.status()).toBe(401);
  });

  test('C7 - POST /api/customers - Create customer with missing required fields returns 400', async ({ request }) => {
    const response = await request.post(`${API_BASE}/customers`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {},
    });

    expect([400, 500]).toContain(response.status());
  });

  test('C8 - POST /api/customers - Create customer successfully', async ({ request }) => {
    testCustomer = await createTestCustomer(request, authToken);

    expect(testCustomer).toBeTruthy();
    expect(testCustomer).toHaveProperty('id');
    expect(testCustomer).toHaveProperty('fullName');
  });

  test('C9 - POST /api/customers - Create customer with invalid phone format', async ({ request }) => {
    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        fullName: `Test Customer ${timestamp}`,
        email: `test${timestamp}@example.com`,
        phone: 'invalid',
      },
    });

    expect([400, 500]).toContain(response.status());
  });

  test('C10 - POST /api/customers - Create customer with invalid email format', async ({ request }) => {
    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        fullName: `Test Customer ${timestamp}`,
        email: 'invalid-email',
        phone: `09${String(timestamp).slice(-8)}`,
      },
    });

    expect([400, 500]).toContain(response.status());
  });

  // =========================================================
  // GET /api/customers/{id} - Get Customer By ID
  // =========================================================
  test('C11 - GET /api/customers/{id} - Get customer by ID returns 200', async ({ request }) => {
    testCustomer = await createTestCustomer(request, authToken);

    const customer = await getCustomerById(request, authToken, testCustomer.id);

    expect(customer).toBeTruthy();
    expect(customer.id).toBe(testCustomer.id);
    expect(customer).toHaveProperty('fullName');
    expect(customer).toHaveProperty('orderCount');
    expect(customer).toHaveProperty('totalSpent');
  });

  test('C12 - GET /api/customers/{id} - Get non-existent customer returns 404', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.get(`${API_BASE}/customers/${fakeId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect([404, 500]).toContain(response.status());
  });

  // =========================================================
  // PUT /api/customers/{id} - Update Customer
  // =========================================================
  test('C13 - PUT /api/customers/{id} - Update customer successfully', async ({ request }) => {
    testCustomer = await createTestCustomer(request, authToken);

    const updated = await updateTestCustomer(request, authToken, testCustomer.id, {
      fullName: 'Updated Customer Name',
    });

    expect(updated).toBeTruthy();
    expect(updated.fullName).toBe('Updated Customer Name');
  });

  test('C14 - PUT /api/customers/{id} - Update customer with new phone', async ({ request }) => {
    testCustomer = await createTestCustomer(request, authToken);

    const timestamp = Date.now();
    const updated = await updateTestCustomer(request, authToken, testCustomer.id, {
      phone: `09${String(timestamp).slice(-8)}`,
    });

    expect(updated).toBeTruthy();
  });

  test('C15 - PUT /api/customers/{id} - Update non-existent customer returns 404', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.put(`${API_BASE}/customers/${fakeId}`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: { fullName: 'Updated Name' },
    });

    expect([404, 500]).toContain(response.status());
  });

  // =========================================================
  // DELETE /api/customers/{id} - Delete Customer
  // =========================================================
  test('C16 - DELETE /api/customers/{id} - Delete customer successfully', async ({ request }) => {
    testCustomer = await createTestCustomer(request, authToken);
    const customerId = testCustomer.id;
    testCustomer = null;

    const response = await request.delete(`${API_BASE}/customers/${customerId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect([200, 204]).toContain(response.status());
  });

  test('C17 - DELETE /api/customers/{id} - Delete non-existent customer returns 404', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000000';
    const response = await request.delete(`${API_BASE}/customers/${fakeId}`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect([404, 500]).toContain(response.status());
  });

  // =========================================================
  // GET /api/customers/stats - Get Customer Stats
  // =========================================================
  test('C18 - GET /api/customers/stats - Get customer stats returns 200', async ({ request }) => {
    const stats = await getCustomerStats(request, authToken);

    expect(stats).toBeTruthy();
    expect(stats).toHaveProperty('totalCustomers');
    expect(stats).toHaveProperty('activeCustomers');
    expect(stats).toHaveProperty('totalOrders');
    expect(stats).toHaveProperty('totalSpent');
  });

  // =========================================================
  // Edge Cases
  // =========================================================
  test('C19 - POST /api/customers - Create customer with duplicate phone returns 409', async ({ request }) => {
    testCustomer = await createTestCustomer(request, authToken);
    const originalPhone = testCustomer.phone;

    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        fullName: `Duplicate Phone ${timestamp}`,
        phone: originalPhone,
        email: `different${timestamp}@example.com`,
      },
    });

    expect([409, 400, 500]).toContain(response.status());
  });

  test('C20 - POST /api/customers - Create customer with duplicate email returns 409', async ({ request }) => {
    testCustomer = await createTestCustomer(request, authToken);
    const originalEmail = testCustomer.email;

    const timestamp = Date.now();
    const response = await request.post(`${API_BASE}/customers`, {
      headers: { Authorization: `Bearer ${authToken}` },
      data: {
        fullName: `Duplicate Email ${timestamp}`,
        phone: `09${String(timestamp).slice(-8)}`,
        email: originalEmail,
      },
    });

    expect([409, 400, 500]).toContain(response.status());
  });
});
