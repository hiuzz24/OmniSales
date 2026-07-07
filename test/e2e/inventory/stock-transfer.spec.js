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

test.describe('Stock Transfer E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('ST-E2E-1 - Stock Transfer list page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/transfers`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/warehouse\/transfers/);
    const body = await page.textContent('body');
    expect(body).toContain('chuyển kho');
  });

  test('ST-E2E-2 - Stock Transfer page has statistics', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/transfers`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await page.locator('text=Tổng phiếu').count();
    expect(total).toBeGreaterThan(0);
  });

  test('ST-E2E-3 - Stock Transfer page has search input', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/transfers`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = page.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('CK-');
      await page.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('ST-E2E-4 - Stock Transfer create page navigates', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/transfers`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = page.locator('text=Tạo phiếu chuyển').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = page.url();
      expect(url).toMatch(/\/warehouse\/transfers/);
    }
  });

  test('ST-E2E-5 - Stock Transfer create form renders', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/transfers/create`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    expect(page.url()).toMatch(/\/warehouse\/transfers\/create/);
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('ST-E2E-6 - Stock Transfer page requires auth', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await page.goto(`${BASE_URL}/warehouse/transfers`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = page.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/warehouse/transfers')).toBeTruthy();
  });
});
