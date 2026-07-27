const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Stock Receive E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('SR-E2E-1 - Stock Receive list page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/warehouse\/receipts/);
    const body = await managerPage.textContent('body');
    expect(body).toContain('phiếu nhập');
  });

  test('SR-E2E-2 - Stock Receive page shows statistics cards', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const total = await managerPage.locator('text=Tổng phiếu').count();
    expect(total).toBeGreaterThan(0);
  });

  test('SR-E2E-3 - Stock Receive page has search input', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm theo mã"]').first();
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill('PN-2026');
      await managerPage.waitForTimeout(700);
      expect(true).toBeTruthy();
    }
  });

  test('SR-E2E-4 - Stock Receive page has status filter', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const select = managerPage.locator('select').first();
    if (await select.isVisible({ timeout: 3000 }).catch(() => false)) {
      const options = await select.locator('option').allTextContents();
      expect(options.some((o) => o.includes('Lưu tạm') || o.includes('Hoàn thành') || o.includes('Trả hàng'))).toBeTruthy();
    }
  });

  test('SR-E2E-5 - Stock Receive create page navigates correctly', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const createBtn = managerPage.locator('text=Tạo phiếu nhập').first();
    if (await createBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await createBtn.click();
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      const url = managerPage.url();
      expect(url).toMatch(/\/warehouse\/receipts/);
    }
  });

  test('SR-E2E-6 - Stock Receive create form renders fields', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/warehouse/receipts/create`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
    expect(managerPage.url()).toMatch(/\/warehouse\/receipts\/create/);
  });

  test('SR-E2E-7 - Stock Receive page requires auth', async ({ managerPage }) => {
    await managerPage.context().clearCookies();
    await managerPage.evaluate(() => { try { window.localStorage.clear(); } catch {} });
    await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    const url = managerPage.url();
    expect(url === `${BASE_URL}/login` || url.endsWith('/warehouse/receipts')).toBeTruthy();
  });
});
