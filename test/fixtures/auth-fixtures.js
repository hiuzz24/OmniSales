/**
 * Custom Playwright fixtures for OmniSales.
 *
 * Use this module's `test` instead of `@playwright/test` to get
 * pre-authenticated page and request fixtures:
 *
 * E2E Tests (page-based):
 *   const { test, expect } = require('../fixtures/auth-fixtures');
 *   test('product list renders', async ({ managerPage }) => {
 *     await managerPage.goto('/products');
 *   });
 *
 * API Tests (request-based):
 *   const { test, expect } = require('../fixtures/auth-fixtures');
 *   test('GET /api/products', async ({ request, adminHeaders }) => {
 *     const response = await request.get(`${API_BASE}/products`, {
 *       headers: adminHeaders,
 *     });
 *   });
 *
 * Fixtures:
 *   - managerPage   : page already logged in as the test manager account
 *   - managerHeaders: object with Authorization header for manager user
 *   - adminHeaders  : object with Authorization header for admin user
 */

const base = require('@playwright/test');
const { loginAsManager } = require('../utils/product-helpers');
const { getAuthHeaders } = require('../utils/warehouse-helpers');
const { getAdminAuthHeadersCached } = require('../utils/admin-helpers');

exports.test = base.test.extend({
  managerPage: async ({ page }, use) => {
    await loginAsManager(page);
    await use(page);
  },

  managerHeaders: async ({ request }, use) => {
    const headers = await getAuthHeaders(request);
    await use(headers);
  },

  adminHeaders: async ({ request }, use) => {
    const headers = await getAdminAuthHeadersCached(request);
    await use(headers);
  },

  // adminPage is intentionally not provided: there is no dedicated
  // admin-login flow different from the manager flow, and admin-only
  // checks happen on the API side. If you need UI testing for admin
  // pages, prefer the inventory-helpers loginAsOwner with admin creds
  // (or add it here later).
});

exports.expect = base.expect;
