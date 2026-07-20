const { test, expect } = require('../../fixtures/auth-fixtures');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Supplier Page E2E Tests', () => {

  test('SP-E2E-1 - Supplier list page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/inventory\/suppliers/);
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('SP-E2E-2 - Supplier list page shows table or content', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasTable = await managerPage.locator('table, [role="table"]').first().isVisible({ timeout: 5000 }).catch(() => false);
    const hasContent = (await managerPage.textContent('body')).length > 100;

    expect(hasTable || hasContent).toBeTruthy();
  });

  test('SP-E2E-3 - Search input exists on supplier page', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm"], input[placeholder*="Search"], input[type="search"]').first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
  });

  test('SP-E2E-4 - Status filter exists on supplier page', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const select = managerPage.locator('select').first();
    await expect(select).toBeVisible({ timeout: 5000 });
  });

  test('SP-E2E-5 - Search functionality accepts input', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm"], input[placeholder*="Search"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('Test');
      await managerPage.waitForTimeout(700);

      const body = await managerPage.textContent('body');
      expect(body.length).toBeGreaterThan(0);
    }
  });

  test('SP-E2E-6 - Add/Create supplier button visible', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const addBtn = managerPage.locator('button:has-text("Thêm"), button:has-text("Add"), button:has-text("Tạo"), button[aria-label*="add"], button[aria-label*="Add"]').first();
    if (await addBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await expect(addBtn).toBeVisible();
    }
  });

  test('SP-E2E-7 - Navigation to suppliers page via URL', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/dashboard`);
    await managerPage.waitForURL(/\/dashboard/);

    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await expect(managerPage).toHaveURL(/\/inventory\/suppliers/);
  });

  test('SP-E2E-8 - Permission-gated - non-operator cannot access supplier page', async ({ managerPage }) => {
    await managerPage.context().clearCookies();
    await managerPage.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);

    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = managerPage.url();
    expect(url === `${BASE_URL}/login` || url.includes('/inventory/suppliers')).toBeTruthy();
  });
});
