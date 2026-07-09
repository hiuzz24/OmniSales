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

test.describe('Channel Connection Page E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('CH-E2E-1 - Channel list page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(page).toHaveURL(/\/channels$/);
    const body = await page.textContent('body');
    expect(body).toContain('Kênh Bán hàng');
  });

  test('CH-E2E-2 - Stats cards visible (connected count, active count, platforms)', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const stats = await page.locator('[class*="statCard"], [class*="stat"]').count();
    expect(stats).toBeGreaterThan(0);
  });

  test('CH-E2E-3 - "Thêm kênh mới" (Add channel) button visible', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const addBtn = page.locator('button:has-text("Thêm kênh mới"), button:has-text("Thêm kênh")').first();
    await expect(addBtn).toBeVisible({ timeout: 5000 });
  });

  test('CH-E2E-4 - "Lịch sử kết nối" (History) button navigates to history page', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const historyBtn = page.locator('button:has-text("Lịch sử kết nối")').first();
    if (await historyBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await historyBtn.click();
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      await expect(page).toHaveURL(/\/channels\/connection-history/);
    }
  });

  test('CH-E2E-5 - Open add channel modal and verify form fields', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await page.locator('button:has-text("Thêm kênh mới")').first().click();
    await page.waitForTimeout(500);

    // Check modal/form has platform select or platform field
    const platformLabel = page.locator('text=/Nền tảng|Platform/i').first();
    await expect(platformLabel).toBeVisible({ timeout: 3000 });

    // Cancel/close modal to avoid polluting state
    const cancelBtn = page.locator('button:has-text("Hủy"), button:has-text("Cancel"), button[aria-label*="close"], button[aria-label*="Close"]').first();
    if (await cancelBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
      await cancelBtn.click();
    }
  });

  test('CH-E2E-6 - Channel list page shows empty state or list', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const table = page.locator('table').first();
    const emptyState = page.locator('text=/Chưa có kênh/i').first();

    const hasTable = await table.isVisible({ timeout: 3000 }).catch(() => false);
    const hasEmpty = await emptyState.isVisible({ timeout: 3000 }).catch(() => false);

    expect(hasTable || hasEmpty).toBeTruthy();
  });

  test('CH-E2E-7 - Channel list does not crash with rapid reloads', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.goto(`${BASE_URL}/channels`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await page.textContent('body');
    expect(body).toContain('Kênh Bán hàng');
  });
});

test.describe('Channel Connection History Page E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  test('CH-HIST-1 - History page loads', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels/connection-history`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(page).toHaveURL(/\/channels\/connection-history/);
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  test('CH-HIST-2 - History page renders content', async ({ page }) => {
    await page.goto(`${BASE_URL}/channels/connection-history`);
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    // Page should at least render content (cards, table, list...)
    const hasCards = await page.locator('[class*="card"]').count();
    expect(hasCards).toBeGreaterThan(0);
  });
});
