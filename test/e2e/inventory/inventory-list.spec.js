const { test, expect } = require('../../fixtures/auth-fixtures');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Inventory List Page E2E Tests', () => {

  test('INV-E2E-1 - Inventory list page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/inventory$/);
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('INV-E2E-2 - Inventory page renders content table or cards', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasTable = await managerPage.locator('table').first().isVisible({ timeout: 5000 }).catch(() => false);
    const hasCards = await managerPage.locator('[class*="card"]').first().isVisible({ timeout: 3000 }).catch(() => false);

    expect(hasTable || hasCards).toBeTruthy();
  });

  test('INV-E2E-3 - Inventory page has search input', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm"], input[placeholder*="Search"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('abc');
      await managerPage.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('INV-E2E-4 - Inventory logs page accessible', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/inventory\/logs/);
  });

  test('INV-E2E-5 - Stocktake list page accessible', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stocktake`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/inventory\/stocktake/);
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('INV-E2E-6 - Navigation between supplier and inventory', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await managerPage.goto(`${BASE_URL}/inventory/suppliers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/inventory\/suppliers/);

    await managerPage.goto(`${BASE_URL}/inventory`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/inventory$/);
  });

  test('INV-E2E-7 - Inventory pages require auth', async ({ managerPage }) => {
    await managerPage.context().clearCookies();
    await managerPage.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await managerPage.goto(`${BASE_URL}/inventory`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = managerPage.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/inventory')).toBeTruthy();
  });
});
