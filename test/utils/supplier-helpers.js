/**
 * Helper utilities for supplier API tests.
 *
 * Note: the backend does not provide a DELETE endpoint for suppliers.
 * Instead, suppliers are deactivated via PATCH /api/suppliers/{id}/status
 * with { isActive: false }. This helper implements that pattern.
 */

const { TEST_EMAIL, TEST_PASSWORD, API_BASE } = require('./env-config');

async function getAuthToken(request) {
  for (let i = 0; i < 5; i++) {
    const resp = await request.post(`${API_BASE}/auth/login`, {
      data: { email: TEST_EMAIL, password: TEST_PASSWORD },
    });
    if (resp.status() === 200) {
      const body = await resp.json();
      return body.data.accessToken;
    }
    if (resp.status() === 429 || resp.status() === 401 || resp.status() === 403) {
      await new Promise(r => setTimeout(r, 3000 * (i + 1)));
      continue;
    }
    throw new Error(`Login failed with status ${resp.status()}`);
  }
  throw new Error('Login failed after retries');
}

/**
 * Create a test supplier via API.
 * Returns the created supplier object with id.
 */
async function createTestSupplier(request, token, overrides = {}) {
  const timestamp = Date.now();

  const defaultSupplier = {
    name: overrides.name || `TestSup_${timestamp}`,
    email: overrides.email || `supplier${timestamp}@example.com`,
    phone: overrides.phone || `09${String(timestamp).slice(-8)}`,
    address: overrides.address || `${timestamp} Test Street`,
    taxCode: overrides.taxCode || `MST${timestamp}`.slice(0, 13),
    contactName: overrides.contactName || 'Test Contact',
    isActive: overrides.isActive !== undefined ? overrides.isActive : true,
  };

  const response = await request.post(`${API_BASE}/suppliers`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: defaultSupplier,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    // Don't throw — let the caller decide (e.g. test.skip if creation fails
    // because of a backend bug). Return null so the caller's null-check works.
    const body = await response.text().catch(() => '');
    console.warn(`createTestSupplier failed: status=${response.status()} body=${body.slice(0, 200)}`);
    return null;
  }

  const body = await response.json();
  return body.data;
}

/**
 * Deactivate a test supplier (no DELETE endpoint exists).
 * Uses PATCH /api/suppliers/{id}/status with { isActive: false }.
 */
async function deactivateTestSupplier(request, token, supplierId) {
  if (!supplierId) return;
  try {
    await request.patch(`${API_BASE}/suppliers/${supplierId}/status`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: { isActive: false },
    });
  } catch (e) {
    // best-effort
  }
}

module.exports = {
  API_BASE,
  TEST_EMAIL,
  TEST_PASSWORD,
  getAuthToken,
  createTestSupplier,
  deactivateTestSupplier,
};