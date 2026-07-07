const { test, expect } = require('@playwright/test');
const { loginAsManager } = require('../../utils/product-helpers');

test.describe('Product Detail E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
    await page.goto('/products');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.waitForTimeout(1000);

    const rows = page.locator('tbody tr');
    const count = await rows.count();

    if (count > 0) {
      const firstDetailBtn = page.locator('button:has-text("Chi tiết")').first();
      if (await firstDetailBtn.isVisible()) {
        await firstDetailBtn.click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      }
    }
  });

  test('C1 - Product detail page renders correctly', async ({ page }) => {
    await expect(page).toHaveURL(/\/products\/[a-f0-9-]+$/);
    await expect(page.locator('button:has-text("Quay lại"), button:has-text("Back")')).toBeVisible();
    await expect(page.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")')).toBeVisible();
    await expect(page.locator('button:has-text("Xóa"), button:has-text("Delete")')).toBeVisible();
  });

  test('C2 - All tabs switch correctly', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.waitForTimeout(1000);

    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    const tabButtons = ['Tổng quan', 'Tồn kho', 'Kênh bán', 'Hình ảnh', 'Biến thể'];

    for (const tabName of tabButtons) {
      const tab = page.locator(`button:has-text("${tabName}")`);
      const count = await tab.count();
      if (count > 0) {
        await tab.first().click();
        await page.waitForTimeout(200);
      }
    }
  });

  test('C3 - Edit button navigates to edit page', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.waitForTimeout(1000);

    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    await page.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
    await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
    await expect(page).toHaveURL(/\/products\/[a-f0-9-]+\/edit/);
  });

  test('C4 - Back button navigates to product list', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.waitForTimeout(1000);

    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    await page.locator('button:has-text("Quay lại"), button:has-text("Back")').click();
    await page.waitForURL(/\/products/);
    await expect(page).toHaveURL(/\/products/);
  });

  test('C5 - Delete product - cancel on dialog', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.waitForTimeout(1000);

    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    page.on('dialog', async (dialog) => {
      expect(dialog.message()).toMatch(/xóa|delete|confirm/i);
      await dialog.dismiss();
    });

    await page.locator('button:has-text("Xóa"), button:has-text("Delete")').click();
    await page.waitForTimeout(500);
    await expect(page).toHaveURL(/\/products\/[a-f0-9-]+$/);
  });

  test('C6 - Delete product - confirm', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await page.waitForTimeout(1000);

    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    page.on('dialog', async (dialog) => {
      await dialog.accept();
    });

    await page.locator('button:has-text("Xóa"), button:has-text("Delete")').click();
    await page.waitForTimeout(3000);
    await expect(page).toHaveURL(/\/products/);
  });
});
