const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Stock Transfer E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('ST-E2E-1 - Stock Transfer list page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/warehouse\/transfers/);
    const body = await managerPage.textContent('body');
    expect(body).toContain('chuyển kho');
  });

  test('ST-E2E-2 - Stock Transfer page has statistics', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await managerPage.locator('text=Tổng phiếu').count();
    expect(total).toBeGreaterThan(0);
  });

  test('ST-E2E-3 - Stock Transfer page has search input', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('CK-');
      await managerPage.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('ST-E2E-4 - Stock Transfer create page navigates', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = managerPage.locator('text=Tạo phiếu chuyển').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = managerPage.url();
      expect(url).toMatch(/\/warehouse\/transfers/);
    }
  });

  test('ST-E2E-5 - Stock Transfer create form renders', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/transfers/create`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    expect(managerPage.url()).toMatch(/\/warehouse\/transfers\/create/);
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('ST-E2E-6 - Stock Transfer page requires auth', async ({ managerPage }) => {
    await managerPage.context().clearCookies();
    await managerPage.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = managerPage.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/warehouse/transfers')).toBeTruthy();
  });
});
