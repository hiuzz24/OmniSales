const { test, expect } = require('@playwright/test');
const { getAuthToken, API_BASE } = require('../../utils/inventory-helpers');

test.describe('Address API Tests', () => {

  let authToken;

  test.beforeAll(async ({ request }) => {
    authToken = await getAuthToken(request);
    expect(authToken).toBeTruthy();
  });

  // ADDR-1
  test('ADDR-1 - GET /api/address/countries - Returns list of countries', async ({ request }) => {
    const response = await request.get(`${API_BASE}/address/countries`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data.length).toBeGreaterThan(0);
  });

  // ADDR-2
  test('ADDR-2 - GET /api/address/countries - Each country has code and name', async ({ request }) => {
    const response = await request.get(`${API_BASE}/address/countries`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    if (body.data.length > 0) {
      const country = body.data[0];
      expect(country).toHaveProperty('code');
      expect(country).toHaveProperty('name');
    }
  });

  // ADDR-3
  test('ADDR-3 - GET /api/address/divisions?country=VN&level=1 - Get provinces', async ({ request }) => {
    const response = await request.get(`${API_BASE}/address/divisions?country=VN&level=1`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // ADDR-4
  test('ADDR-4 - GET /api/address/divisions?country=VN&level=2&parent=... - Get districts', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/address/divisions?country=VN&level=2&parent=HN`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // ADDR-5
  test('ADDR-5 - GET /api/address/divisions?country=INVALID&level=1 - Returns empty or error', async ({ request }) => {
    const response = await request.get(
      `${API_BASE}/address/divisions?country=ZZINVALID&level=1`,
      { headers: { Authorization: `Bearer ${authToken}` } }
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);
  });

  // ADDR-6
  test('ADDR-6 - GET /api/address/divisions - Missing required params returns 4xx/5xx', async ({ request }) => {
    const response = await request.get(`${API_BASE}/address/divisions`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
  });
});
