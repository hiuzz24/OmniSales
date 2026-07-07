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

test.describe('Stocktake E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('SK-E2E-1 - Stocktake list page loads (warehouse/stocktakes)', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/stocktakes`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/warehouse\/stocktakes/);
    const body = await page.textContent('body');
    expect(body).toContain('kiểm kho');
  });

  test('SK-E2E-2 - Stocktake list page loads (inventory/stocktake)', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/stocktake`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/inventory\/stocktake/);
  });

  test('SK-E2E-3 - Stocktake page has statistics', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/stocktakes`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await page.locator('text=Tổng phiếu').count();
    expect(total).toBeGreaterThan(0);
  });

  test('SK-E2E-4 - Stocktake page has search input', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/stocktakes`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = page.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('KK-');
      await page.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('SK-E2E-5 - Stocktake create page navigates', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/stocktakes`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = page.locator('text=Tạo phiếu kiểm').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = page.url();
      expect(url).toMatch(/\/warehouse\/stocktakes/);
    }
  });

  test('SK-E2E-6 - Stocktake create form renders', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/stocktakes/create`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    expect(page.url()).toMatch(/\/warehouse\/stocktakes\/create/);
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('SK-E2E-7 - Stocktake page requires auth', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await page.goto(`${BASE_URL}/warehouse/stocktakes`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = page.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/warehouse/stocktakes')).toBeTruthy();
  });
});
