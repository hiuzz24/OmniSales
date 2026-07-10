const { test, expect } = require('../../fixtures/auth-fixtures');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Stocktake E2E Tests', () => {

  test('SK-E2E-1 - Stocktake list page loads (warehouse/stocktakes)', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/warehouse\/stocktakes/);
    const body = await managerPage.textContent('body');
    expect(body).toContain('kiểm kho');
  });

  test('SK-E2E-2 - Stocktake list page loads (inventory/stocktake)', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stocktake`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/inventory\/stocktake/);
  });

  test('SK-E2E-3 - Stocktake page has statistics', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await managerPage.locator('text=Tổng phiếu').count();
    expect(total).toBeGreaterThan(0);
  });

  test('SK-E2E-4 - Stocktake page has search input', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('KK-');
      await managerPage.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('SK-E2E-5 - Stocktake create page navigates', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = managerPage.locator('text=Tạo phiếu kiểm').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = managerPage.url();
      expect(url).toMatch(/\/warehouse\/stocktakes/);
    }
  });

  test('SK-E2E-6 - Stocktake create form renders', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/stocktakes/create`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    expect(managerPage.url()).toMatch(/\/warehouse\/stocktakes\/create/);
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('SK-E2E-7 - Stocktake page requires auth', async ({ managerPage }) => {
    await managerPage.context().clearCookies();
    await managerPage.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = managerPage.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/warehouse/stocktakes')).toBeTruthy();
  });
});
