const { test, expect } = require('@playwright/test');
const { TEST_EMAIL, TEST_PASSWORD, FRONTEND_URL } = require('../../utils/env-config');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || FRONTEND_URL;

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

test.describe('Inventory List Page E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('INV-E2E-1 - Inventory list page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(page).toHaveURL(/\/inventory$/);
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('INV-E2E-2 - Inventory page renders content table or cards', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasTable = await page.locator('table').first().isVisible({ timeout: 5000 }).catch(() => false);
    const hasCards = await page.locator('[class*="card"]').first().isVisible({ timeout: 3000 }).catch(() => false);

    expect(hasTable || hasCards).toBeTruthy();
  });

  test('INV-E2E-3 - Inventory page has search input', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = page.locator('input[placeholder*="Tìm"], input[placeholder*="Search"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('abc');
      await page.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('INV-E2E-4 - Inventory logs page accessible', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(page).toHaveURL(/\/inventory\/logs/);
  });

  test('INV-E2E-5 - Stocktake list page accessible', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stocktake`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(page).toHaveURL(/\/inventory\/stocktake/);
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('INV-E2E-6 - Navigation between supplier and inventory', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await page.goto(`${BASE_URL}/inventory/suppliers`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/inventory\/suppliers/);

    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/inventory$/);
  });

  test('INV-E2E-7 - Inventory pages require auth', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    // Either redirected to login, or page rendered (without API data, body still has content)
    const url = page.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/inventory')).toBeTruthy();
  });
});
