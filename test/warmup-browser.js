/**
 * Browser warmup for Vite 8 dev server in CI.
 *
 * `curl` alone is not enough: Vite triggers on-demand dependency
 * optimization the first time a real browser loads a route, fetches
 * main.jsx, and follows the import graph. We need a real Chromium to
 * force that work to happen BEFORE the Playwright test run, otherwise
 * the very first test's `waitForURL('**/dashboard', { timeout: 60000 })`
 * hits the cold-start cost and fails.
 *
 * This script:
 *  1. Visits every route tests will exercise, so each lazy chunk gets
 *     pre-optimized.
 *  2. Submits a real login form so the post-login routes (dashboard,
 *     admin, ...) also get optimized.
 *  3. Sleeps a few seconds after the browser warmup so Rolldown's
 *     async finalize phase completes.
 */

const { chromium } = require('@playwright/test');
const { TEST_EMAIL, TEST_PASSWORD, FRONTEND_URL } = require('./utils/env-config');

const ROUTES = [
  '/',
  '/login',
  '/dashboard',
  '/admin',
  '/admin/api-monitor',
  '/products',
  '/inventory',
  '/orders',
  '/customers',
  '/purchase',
  '/suppliers',
  '/settings',
];

(async () => {
  console.log(`[warmup] launching chromium against ${FRONTEND_URL}`);
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  page.on('pageerror', (err) => {
    console.warn(`[warmup] page error: ${err.message}`);
  });
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      console.warn(`[warmup] console error: ${msg.text()}`);
    }
  });

  // 1. Cold-load the entry chain so Vite optimizes React, router, axios, etc.
  console.log('[warmup] cold-loading /');
  await page.goto(`${FRONTEND_URL}/`, { waitUntil: 'load', timeout: 90000 });

  // 2. Hit every route so the route-level chunks get optimized.
  for (const route of ROUTES) {
    if (route === '/') continue;
    try {
      console.log(`[warmup] visiting ${route}`);
      await page.goto(`${FRONTEND_URL}${route}`, { waitUntil: 'load', timeout: 90000 });
    } catch (err) {
      console.warn(`[warmup] failed ${route}: ${err.message}`);
    }
  }

  // 3. Run the real login flow so /dashboard and the manager landing
  //    route chunks get optimized as well.
  console.log('[warmup] running real login flow');
  try {
    await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'load', timeout: 90000 });
    await page.locator('#login-email').waitFor({ state: 'visible', timeout: 90000 });
    await page.locator('#login-email').fill(TEST_EMAIL);
    await page.locator('#login-password').waitFor({ state: 'visible', timeout: 5000 });
    await page.locator('#login-password').fill(TEST_PASSWORD);
    await Promise.all([
      page.waitForURL('**/dashboard', { timeout: 60000 }),
      page.locator('#login-submit-btn').click(),
    ]);
    console.log('[warmup] login ok, on dashboard');
  } catch (err) {
    console.warn(`[warmup] login warmup failed: ${err.message}`);
  }

  // 4. Give Rolldown's async optimizer a moment to finalize.
  console.log('[warmup] sleeping 10s for async finalize');
  await page.waitForTimeout(10000);

  await context.close();
  await browser.close();
  console.log('[warmup] done');
})();
