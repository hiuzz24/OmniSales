/**
 * Helper utilities for product E2E and API tests
 * Uses the same credentials as auth.spec.js
 */

const {
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE: ENV_API_BASE,
} = require('./env-config');
const API_BASE = process.env.API_BASE || ENV_API_BASE;

/**
 * Login as manager via UI (for E2E tests)
 * Navigates to /login, fills credentials, waits for redirect to dashboard
 */
async function loginAsManager(page) {
  await page.goto('/login');
  await page.locator('#login-email').fill(TEST_EMAIL);
  await page.locator('#login-password').fill(TEST_PASSWORD);

  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 8000 }),
    page.locator('#login-submit-btn').click(),
  ]);

  await expect(page).toHaveURL(/\/dashboard/);
}

/**
 * Login via API and return access token
 * For API tests that need authentication
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
 * Create a test product via API
 * Returns the created product object with id and sku
 * Requires valid auth token
 */
async function createTestProduct(request, token, overrides = {}) {
  const timestamp = Date.now();

  // If categoryId not provided, fetch it automatically
  let categoryId = overrides.categoryId;
  if (!categoryId) {
    categoryId = await getFirstCategoryId(request, token);
  }

  const defaultProduct = {
    name: `Test Product ${timestamp}`,
    sku: `TEST-${timestamp}`,
    status: 'ACTIVE',
    lowStockThreshold: 5,
    images: ['https://via.placeholder.com/300'],
    variants: [
      {
        sku: `TEST-V-${timestamp}`,
        price: 150000,
        costPrice: 80000,
        isActive: true,
        optionValues: { Size: 'M', 'Màu': 'Đen' },
      },
    ],
  };

  const productData = { ...defaultProduct, ...overrides };

  // Only set categoryId if we have one (backend requires valid categoryId)
  if (categoryId) {
    productData.categoryId = categoryId;
  }

  const response = await request.post(`${API_BASE}/products`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: productData,
  });

  if (response.status() !== 200) {
    const errorBody = await response.text();
    throw new Error(`Failed to create test product: ${response.status()} - ${errorBody}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Delete a test product via API
 * Best effort - does not throw on failure
 */
async function deleteTestProduct(request, token, productId) {
  try {
    await request.delete(`${API_BASE}/products/${productId}/delete`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (e) {
    // Ignore cleanup errors
  }
}

/**
 * Get list of categories for seeding categoryId in tests
 */
async function getFirstCategoryId(request, token) {
  try {
    const response = await request.get(`${API_BASE}/categories`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (response.status() === 200) {
      const body = await response.json();
      if (body.data && body.data.length > 0) {
        return body.data[0].id;
      }
    }
  } catch (e) {
    // Fall through
  }
  return null;
}

/**
 * Return the first available category name (so tests can fill it into
 * the import template's "Danh mục" column).
 */
async function getFirstCategoryName(request, token) {
  try {
    const response = await request.get(`${API_BASE}/categories`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (response.status() === 200) {
      const body = await response.json();
      if (body.data && body.data.length > 0) {
        return body.data[0].name;
      }
    }
  } catch (e) {
    // Fall through
  }
  return null;
}

/**
 * Generate a unique SKU for test isolation
 */
function uniqueSku(prefix = 'TEST') {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 99999)}`;
}

// Import expect for use in helper functions
const { expect } = require('@playwright/test');

module.exports = {
  loginAsManager,
  getAuthToken,
  getAuthHeaders,
  createTestProduct,
  deleteTestProduct,
  getFirstCategoryId,
  getFirstCategoryName,
  uniqueSku,
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
};
