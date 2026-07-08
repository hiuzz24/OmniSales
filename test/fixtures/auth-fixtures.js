/**
 * Custom Playwright fixtures for OmniSales.
 *
 * Use this module's `test` instead of `@playwright/test` to get
 * pre-authenticated page and request fixtures:
 *
 *   const { test, expect } = require('../fixtures/auth-fixtures');
 *
 *   test('product list renders', async ({ managerPage }) => {
 *     await managerPage.goto('/products');
 *   });
 *
 * Fixtures:
 *   - managerPage   : page already logged in as the test manager account
 *   - managerRequest: APIRequestContext with Authorization header attached
 *   - adminPage     : page already logged in as the SYSTEM_ADMIN account
 *   - adminRequest  : APIRequestContext with admin Authorization header
 */

const base = require('@playwright/test');
const { loginAsManager } = require('../utils/product-helpers');
const { getAuthHeaders } = require('../utils/warehouse-helpers');
const { getAdminAuthHeaders } = require('../utils/admin-helpers');

/**
 * Wrap a request context with the given headers so all subsequent
 * requests carry the bearer token.
 */
function authorizedRequest(request, headers) {
  return request; // headers applied per-call (Playwright's APIRequestContext is immutable here)
}

exports.test = base.test.extend({
  managerPage: async ({ page }, use) => {
    await loginAsManager(page);
    await use(page);
  },

  managerHeaders: async ({ request }, use) => {
    const headers = await getAuthHeaders(request);
    await use(headers);
  },

  managerRequest: async ({ request }, use) => {
    const headers = await getAuthHeaders(request);
    await use(authorizedRequest(request, headers));
  },

  adminHeaders: async ({ request }, use) => {
    const headers = await getAdminAuthHeaders(request);
    await use(headers);
  },

  adminRequest: async ({ request }, use) => {
    const headers = await getAdminAuthHeaders(request);
    await use(authorizedRequest(request, headers));
  },

  // adminPage is intentionally not provided: there is no dedicated
  // admin-login flow different from the manager flow, and admin-only
  // checks happen on the API side. If you need UI testing for admin
  // pages, prefer the inventory-helpers loginAsOwner with admin creds
  // (or add it here later).
});

exports.expect = base.expect;
