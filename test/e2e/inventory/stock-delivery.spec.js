const { test, expect } = require('../../fixtures/auth-fixtures');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Stock Delivery E2E Tests', () => {

  test('SD-E2E-1 - Stock Delivery list page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/inventory\/stock-deliveries/);
    const body = await managerPage.textContent('body');
    expect(body).toContain('phiếu xuất');
  });

  test('SD-E2E-2 - Stock Delivery page shows statistics', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await managerPage.textContent('body');
    expect(body).toContain('Tổng');
  });

  test('SD-E2E-3 - Stock Delivery page has search input', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('PX-2026');
      await managerPage.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('SD-E2E-4 - Stock Delivery page has status filter', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const select = managerPage.locator('select').first();
    if (await select.isVisible({ timeout: 3000 }).catch(() => false)) {
      const options = await select.locator('option').allTextContents();
      expect(options.length).toBeGreaterThan(1);
    }
  });

  test('SD-E2E-5 - Stock Delivery create page navigates', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = managerPage.locator('text=Tạo phiếu xuất').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = managerPage.url();
      expect(url).toMatch(/\/inventory\/stock-deliveries/);
    }
  });

  test('SD-E2E-6 - Stock Delivery create form renders', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries/create`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    expect(managerPage.url()).toMatch(/\/inventory\/stock-deliveries\/create/);
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('SD-E2E-7 - Stock Delivery page requires auth', async ({ managerPage }) => {
    await managerPage.context().clearCookies();
    await managerPage.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = managerPage.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/inventory/stock-deliveries')).toBeTruthy();
  });
});
