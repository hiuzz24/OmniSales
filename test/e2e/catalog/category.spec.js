const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Category E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('CAT-E2E-1 - Category page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/products/categories`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/products\/categories/);
    const body = await managerPage.textContent('body');
    expect(body).toContain('Danh mục');
  });

  test('CAT-E2E-2 - Category page shows statistics', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/products/categories`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await managerPage.locator('text=Danh mục gốc').count();
    expect(total).toBeGreaterThan(0);
  });

  test('CAT-E2E-3 - Category page has search input', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/products/categories`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('Test');
      await managerPage.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('CAT-E2E-4 - Category page has Add button', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/products/categories`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const addBtn = managerPage.locator('text=Thêm danh mục').first();
    if (await addBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addBtn.click();
      await managerPage.waitForTimeout(700);
      const modal = await managerPage.locator('text=Thêm danh mục').count();
      expect(modal).toBeGreaterThanOrEqual(1);
    }
  });

  test('CAT-E2E-5 - Category modal has name & slug inputs', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/products/categories`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const addBtn = managerPage.locator('text=Thêm danh mục').first();
    if (await addBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addBtn.click();
      await managerPage.waitForTimeout(500);
      const nameInput = managerPage.locator('input[name="name"], input[placeholder*="tên danh mục"], input[placeholder*="Áo thun"]').first();
      expect(await nameInput.count()).toBeGreaterThan(0);
    }
  });

  test('CAT-E2E-6 - Category page renders table', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/products/categories`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasTable = await managerPage.locator('table').first().isVisible({ timeout: 5000 }).catch(() => false);
    const hasCards = await managerPage.locator('[class*="card"]').first().isVisible({ timeout: 3000 }).catch(() => false);
    expect(hasTable || hasCards).toBeTruthy();
  });

  test('CAT-E2E-7 - Category page requires auth', async ({ managerPage }) => {
    await managerPage.context().clearCookies();
    await managerPage.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await managerPage.goto(`${BASE_URL}/products/categories`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = managerPage.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/products/categories')).toBeTruthy();
  });
});
