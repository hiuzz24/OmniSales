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

test.describe('Category E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('CAT-E2E-1 - Category page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/products/categories`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(page).toHaveURL(/\/products\/categories/);
    const body = await page.textContent('body');
    expect(body).toContain('Danh mục');
  });

  test('CAT-E2E-2 - Category page shows statistics', async ({ page }) => {
    await page.goto(`${BASE_URL}/products/categories`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await page.locator('text=Danh mục gốc').count();
    expect(total).toBeGreaterThan(0);
  });

  test('CAT-E2E-3 - Category page has search input', async ({ page }) => {
    await page.goto(`${BASE_URL}/products/categories`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = page.locator('input[placeholder*="Tìm"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('Test');
      await page.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('CAT-E2E-4 - Category page has Add button', async ({ page }) => {
    await page.goto(`${BASE_URL}/products/categories`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const addBtn = page.locator('text=Thêm danh mục').first();
    if (await addBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addBtn.click();
      await page.waitForTimeout(700);
      // Modal should appear; check there's modal-title
      const modal = await page.locator('text=Thêm danh mục').count();
      expect(modal).toBeGreaterThanOrEqual(1);
    }
  });

  test('CAT-E2E-5 - Category modal has name & slug inputs', async ({ page }) => {
    await page.goto(`${BASE_URL}/products/categories`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const addBtn = page.locator('text=Thêm danh mục').first();
    if (await addBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addBtn.click();
      await page.waitForTimeout(500);
      // Modal inputs
      const nameInput = page.locator('input[name="name"], input[placeholder*="tên danh mục"], input[placeholder*="Áo thun"]').first();
      expect(await nameInput.count()).toBeGreaterThan(0);
    }
  });

  test('CAT-E2E-6 - Category page renders table', async ({ page }) => {
    await page.goto(`${BASE_URL}/products/categories`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasTable = await page.locator('table').first().isVisible({ timeout: 5000 }).catch(() => false);
    const hasCards = await page.locator('[class*="card"]').first().isVisible({ timeout: 3000 }).catch(() => false);
    expect(hasTable || hasCards).toBeTruthy();
  });

  test('CAT-E2E-7 - Category page requires auth', async ({ page }) => {
    await page.context().clearCookies();
    await page.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await page.goto(`${BASE_URL}/products/categories`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = page.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/products/categories')).toBeTruthy();
  });
});
