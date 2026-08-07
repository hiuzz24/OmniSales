const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Channel Connection Page E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('CH-E2E-1 - Channel list page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/channels$/);
    const body = await managerPage.textContent('body');
    expect(body).toContain('Kênh Bán hàng');
  });

  test('CH-E2E-2 - Stats cards visible', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const stats = await managerPage.locator('[class*="statCard"], [class*="stat"]').count();
    expect(stats).toBeGreaterThan(0);
  });

  test('CH-E2E-3 - "Thêm kênh mới" button visible', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const addBtn = managerPage.locator('button:has-text("Thêm kênh mới"), button:has-text("Thêm kênh")').first();
    await expect(addBtn).toBeVisible({ timeout: 5000 });
  });

  test('CH-E2E-4 - "Lịch sử kết nối" navigates to history page', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const historyBtn = managerPage.locator('button:has-text("Lịch sử kết nối")').first();
    if (await historyBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await historyBtn.click();
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      await expect(managerPage).toHaveURL(/\/channels\/connection-history/);
    }
  });

  test('CH-E2E-5 - Open add channel modal and verify form fields', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await managerPage.locator('button:has-text("Thêm kênh mới")').first().click();
    await managerPage.waitForTimeout(500);

    const platformLabel = managerPage.locator('text=/Nền tảng|Platform/i').first();
    await expect(platformLabel).toBeVisible({ timeout: 3000 });

    const cancelBtn = managerPage.locator('button:has-text("Hủy"), button:has-text("Cancel"), button[aria-label*="close"], button[aria-label*="Close"]').first();
    if (await cancelBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
      await cancelBtn.click();
    }
  });

  test('CH-E2E-6 - Channel list page shows empty state or list', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const table = managerPage.locator('table').first();
    const emptyState = managerPage.locator('text=/Chưa có kênh/i').first();

    const hasTable = await table.isVisible({ timeout: 3000 }).catch(() => false);
    const hasEmpty = await emptyState.isVisible({ timeout: 3000 }).catch(() => false);

    expect(hasTable || hasEmpty).toBeTruthy();
  });

  test('CH-E2E-7 - Channel list does not crash with rapid reloads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.goto(`${BASE_URL}/channels`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await managerPage.textContent('body');
    expect(body).toContain('Kênh Bán hàng');
  });
});

test.describe('Channel Connection History Page E2E Tests', () => {

  test('CH-HIST-1 - History page loads', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels/connection-history`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/channels\/connection-history/);
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('CH-HIST-2 - History page renders content', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/channels/connection-history`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasCards = await managerPage.locator('[class*="card"]').count();
    expect(hasCards).toBeGreaterThan(0);
  });
});
