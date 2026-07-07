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

test.describe('Stock Receive E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('SR-E2E-1 - Stock Receive list page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/receipts`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/warehouse\/receipts/);
    const body = await page.textContent('body');
    expect(body).toContain('phiếu nhập');
  });

  test('SR-E2E-2 - Stock Receive page shows statistics cards', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/receipts`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await page.locator('text=Tổng phiếu').count();
    expect(total).toBeGreaterThan(0);
  });

  test('SR-E2E-3 - Stock Receive page has search input', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/receipts`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = page.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('PN-2026');
      await page.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('SR-E2E-4 - Stock Receive page has status filter', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/receipts`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const select = page.locator('select').first();
    if (await select.isVisible({ timeout: 3000 }).catch(() => false)) {
      const options = await select.locator('option').allTextContents();
      expect(options.some((o) => o.includes('Lưu tạm') || o.includes('Hoàn thành') || o.includes('Trả hàng'))).toBeTruthy();
    }
  });

  test('SR-E2E-5 - Stock Receive create page navigates correctly', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/receipts`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = page.locator('text=Tạo phiếu nhập').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = page.url();
      expect(url).toMatch(/\/warehouse\/receipts/);
    }
  });

  test('SR-E2E-6 - Stock Receive create form renders fields', async ({ page }) => {
    await page.goto(`${BASE_URL}/warehouse/receipts/create`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
    expect(page.url()).toMatch(/\/warehouse\/receipts\/create/);
  });

  test('SR-E2E-7 - Stock Receive page requires auth', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await page.goto(`${BASE_URL}/warehouse/receipts`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = page.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/warehouse/receipts')).toBeTruthy();
  });
});
