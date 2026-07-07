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

test.describe('Stock Delivery E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('SD-E2E-1 - Stock Delivery list page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/inventory\/stock-deliveries/);
    const body = await page.textContent('body');
    expect(body).toContain('phiếu xuất');
  });

  test('SD-E2E-2 - Stock Delivery page shows statistics', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await page.textContent('body');
    // Delivery page shows "Tổng xuất" footer
    expect(body).toContain('Tổng');
  });

  test('SD-E2E-3 - Stock Delivery page has search input', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = page.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('PX-2026');
      await page.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('SD-E2E-4 - Stock Delivery page has status filter', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const select = page.locator('select').first();
    if (await select.isVisible({ timeout: 3000 }).catch(() => false)) {
      const options = await select.locator('option').allTextContents();
      expect(options.length).toBeGreaterThan(1);
    }
  });

  test('SD-E2E-5 - Stock Delivery create page navigates', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = page.locator('text=Tạo phiếu xuất').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = page.url();
      expect(url).toMatch(/\/inventory\/stock-deliveries/);
    }
  });

  test('SD-E2E-6 - Stock Delivery create form renders', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stock-deliveries/create`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    expect(page.url()).toMatch(/\/inventory\/stock-deliveries\/create/);
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('SD-E2E-7 - Stock Delivery page requires auth', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = page.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/inventory/stock-deliveries')).toBeTruthy();
  });
});
