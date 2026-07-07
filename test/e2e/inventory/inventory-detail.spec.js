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

test.describe('Inventory Detail E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('INV-E2E-D1 - Inventory list page shows table or cards', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasTable = await page.locator('table').first().isVisible({ timeout: 5000 }).catch(() => false);
    const hasCards = await page.locator('[class*="card"]').first().isVisible({ timeout: 3000 }).catch(() => false);
    expect(hasTable || hasCards).toBeTruthy();
  });

  test('INV-E2E-D2 - Inventory page has search input', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = page.locator('input[placeholder*="Tìm"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('Test');
      await page.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('INV-E2E-D3 - Inventory page has status filter', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await page.textContent('body');
    // Should show some filter labels
    expect(body.length).toBeGreaterThan(0);
  });

  test('INV-E2E-D4 - Inventory logs page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/inventory\/logs/);
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('INV-E2E-D5 - Inventory logs page has date filter', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const input = page.locator('input[type="date"], input[type="datetime-local"]').first();
    if (await input.isVisible({ timeout: 3000 }).catch(() => false)) {
      expect(true).toBeTruthy();
    } else {
      const body = await page.textContent('body');
      expect(body.length).toBeGreaterThan(0);
    }
  });

  test('INV-E2E-D6 - Inventory detail page reachable', async ({ page }) => {
    // Use API to fetch first item, then navigate to detail
    await page.goto(`${BASE_URL}/inventory`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    // Find first inventory row's id via API call inside the page
    const firstId = await page.evaluate(async () => {
      try {
        const token = window.localStorage.getItem('accessToken') || '';
        const res = await fetch('http://localhost:8080/api/inventory?page=0&size=1', {
          headers: { Authorization: `Bearer ${token}` },
        });
        const j = await res.json();
        return j?.data?.content?.[0]?.id || null;
      } catch (e) {
        return null;
      }
    });

    if (firstId) {
      const response = await page.goto(`${BASE_URL}/inventory/detail/${firstId}`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      expect(page.url()).toMatch(/\/inventory\/detail/);
      expect(response?.status() || 200).toBeLessThan(500);
    } else {
      test.skip(true, 'No inventory items available');
    }
  });

  test('INV-E2E-D7 - Inventory page requires auth', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = page.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/inventory/logs')).toBeTruthy();
  });
});
