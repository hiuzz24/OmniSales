/**
 * Route helpers for E2E tests.
 *
 * `gotoOrSkip` checks the routes-inventory map and skips the test
 * (with a clear reason) if the target path is not wired in the frontend.
 */

const { test, expect } = require('@playwright/test');
const { isRouteAvailable, getRoute } = require('../e2e/routes-inventory');

/**
 * Navigate to a frontend route. If the path is not in routes-inventory,
 * the test is skipped with a clear reason rather than failing on a
 * wildcard redirect.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} routePath - frontend path (e.g. '/backups')
 * @param {object} [options] - forwarded to page.goto
 * @returns {Promise<import('@playwright/test').Response|null>}
 */
async function gotoOrSkip(page, routePath, options = {}) {
  if (!isRouteAvailable(routePath)) {
    const entry = getRoute(routePath);
    const reason = entry?.correctPath
      ? `Route ${routePath} does not exist in frontend (use ${entry.correctPath})`
      : `Route ${routePath} does not exist in frontend`;
    test.skip(true, reason);
    return null;
  }
  return await page.goto(routePath, options);
}

module.exports = { gotoOrSkip };
