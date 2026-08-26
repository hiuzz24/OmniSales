/**
 * Các hàm helper cho product E2E và API tests
 * Sử dụng cùng credentials như auth.spec.js
 */

const {
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE: ENV_API_BASE,
} = require('./env-config');
const API_BASE = process.env.API_BASE || ENV_API_BASE;

/**
 * Đăng nhập như manager qua UI (cho E2E tests)
 * Điều hướng đến /login, điền credentials, đợi redirect đến dashboard.
 *
 * Xử lý rate-limit (429) errors bằng cách đợi và thử lại.
 * 30s managerPage fixture timeout là historical flake mode: Vite
 * đôi khi re-optimizes dependencies trên request đầu tiên, nên email
 * input không có trong DOM cho đến khi bundle download xong. Chúng tôi
 * pre-wait cho input visible (90s) trước khi thực hiện bất kỳ action nào.
 */
async function loginAsManager(page) {
  await page.goto('/login');
  // Vite re-optimization có thể mất 30s+; đợi đến 90s cho form.
  await page.locator('#login-email').waitFor({ state: 'visible', timeout: 90000 });
  await page.locator('#login-email').fill(TEST_EMAIL);
  await page.locator('#login-password').waitFor({ state: 'visible', timeout: 5000 });
  await page.locator('#login-password').fill(TEST_PASSWORD);

  // Kiểm tra nếu đã bị rate-limited (xảy ra sau nhiều lần test)
  const errorBanner = page.locator('[role="alert"]');
  if (await errorBanner.isVisible({ timeout: 2000 }).catch(() => false)) {
    const errorText = await errorBanner.textContent();
    if (errorText.includes('Quá nhiều lần thử')) {
      // Đợi 2 phút cho rate-limit reset
      console.log('[login] Rate-limited, đợi 120s để reset...');
      await page.waitForTimeout(120000);
      // Reload page and try again
      await page.goto('/login');
      await page.locator('#login-email').waitFor({ state: 'visible', timeout: 90000 });
      await page.locator('#login-email').fill(TEST_EMAIL);
      await page.locator('#login-password').waitFor({ state: 'visible', timeout: 5000 });
      await page.locator('#login-password').fill(TEST_PASSWORD);
    }
  }

  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 60000 }),
    page.locator('#login-submit-btn').click(),
  ]);

  await expect(page).toHaveURL(/\/dashboard/);
}

/**
 * Đăng nhập qua API và trả về access token
 * Cho API tests cần authentication
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
 *
 * NOTE: `ProductRequest` requires several non-null fields and a structured
 * `images` array (each item is a `{ url, isPrimary, sortOrder }` object).
 * The previous helper sent raw strings + omitted required fields, which made
 * every create call fail with HTTP 500. The defaults below cover the BE
 * validation requirements enforced by ProductRequestValidator.
 */
async function createTestProduct(request, token, overrides = {}) {
  const timestamp = Date.now();

  // If categoryId not provided, fetch it automatically. If no categories
  // exist (e.g. the afterEach hook just deleted the last test category in
  // a fast-running suite), create one on the fly so this test doesn't
  // fail with a misleading 400 "Product category must not be null".
  let categoryId = overrides.categoryId;
  if (!categoryId) {
    categoryId = await getFirstCategoryId(request, token);
    if (!categoryId) {
      categoryId = await ensureCategory(request, token, `Test Cat ${timestamp}`);
    }
  }

  const defaultVariant = {
    sku: `TEST-V-${timestamp}`,
    barcode: `BC-${timestamp}`,
    price: 150000,
    costPrice: 80000,
    isActive: true,
    optionValues: { Size: 'M', 'Màu': 'Đen' },
    images: [
      // Each variant image MUST NOT be primary because there is a UNIQUE
      // constraint on (product_id) WHERE is_primary=true in product_images.
      // A primary image for a variant also occupies the product's primary
      // slot (variant_id is set but product_id is still the parent product).
      { url: 'https://via.placeholder.com/300', isPrimary: false, sortOrder: 1 },
    ],
  };

  const defaultProduct = {
    name: `Test Product ${timestamp}`,
    sku: `TEST-${timestamp}`,
    description: 'Test product description for Playwright E2E tests',
    brand: 'TestBrand',
    unit: 'pcs',
    hasVariants: true,
    status: 'ACTIVE',
    lowStockThreshold: 5,
    weightGrams: 500,
    attributes: {
      packageLengthCm: 20,
      packageWidthCm: 15,
      packageHeightCm: 10,
    },
    images: [
      { url: 'https://via.placeholder.com/300', isPrimary: true, sortOrder: 0 },
    ],
    variants: [defaultVariant],
  };

  const productData = { ...defaultProduct, ...overrides };

  // Normalize variants: ensure each variant is shaped as a ProductVariantRequest
  // (the previous helper accepted `{ sku, price, isActive, optionValues }` but
  // did not normalize; if a test passed custom variants they need barcode + images).
  if (Array.isArray(productData.variants)) {
    productData.variants = productData.variants.map((v, i) => ({
      ...v,
      barcode: v.barcode || `BC-${timestamp}-${i}`,
      name: v.name || `Variant ${v.sku}`,
      weightGrams: v.weightGrams || 500,
      images: v.images || [
        // See defaultVariant above: variant images must NOT be primary because
        // only one primary image is allowed per product_id.
        { url: 'https://via.placeholder.com/300', isPrimary: false, sortOrder: 1 },
      ],
    }));
  }

  // Ensure attributes carry the dimension fields the validator requires.
  productData.attributes = {
    packageLengthCm: 20,
    packageWidthCm: 15,
    packageHeightCm: 10,
    ...(productData.attributes || {}),
  };

  // Only set categoryId if we have one (backend requires valid categoryId)
  if (categoryId) {
    productData.categoryId = categoryId;
  }

  let response;
  for (let attempt = 0; attempt < 6; attempt++) {
    response = await request.post(`${API_BASE}/products`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: productData,
    });
    if (response.status() !== 429) break;
    await new Promise(r => setTimeout(r, 1500 * (attempt + 1) + Math.floor(Math.random() * 500)));
  }

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
 * Create a category on demand and return its id. Used as a fallback when
 * the suite has wiped the catalog (e.g. after a fast afterEach cleanup)
 * but a test still needs to attach a categoryId to its product.
 */
async function ensureCategory(request, token, name) {
  try {
    const response = await request.post(`${API_BASE}/categories`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {
        name,
        slug: `test-cat-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
        parentId: null,
        description: 'Auto-created for Playwright test product',
        sortOrder: 0,
      },
    });
    if (response.status() === 200 || response.status() === 201) {
      const body = await response.json();
      if (body.data && body.data.id) return body.data.id;
    }
  } catch (_) {
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
  ensureCategory,
  getFirstCategoryName,
  uniqueSku,
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
};
