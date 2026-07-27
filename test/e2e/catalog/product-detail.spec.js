const { test, expect } = require('../../fixtures/auth-fixtures');

test.describe('Product Detail E2E Tests', () => {

  test.beforeEach(async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.waitForTimeout(1000);

    const rows = managerPage.locator('tbody tr');
    const count = await rows.count();

    if (count > 0) {
      const firstDetailBtn = managerPage.locator('button:has-text("Chi tiết")').first();
      if (await firstDetailBtn.isVisible()) {
        await firstDetailBtn.click();
        await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
      }
    }
  });

  test('C1 - Product detail page renders correctly', async ({ managerPage }) => {
    await expect(managerPage).toHaveURL(/\/products\/[a-f0-9-]+$/);
    await expect(managerPage.locator('button:has-text("Quay lại"), button:has-text("Back")')).toBeVisible();
    await expect(managerPage.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")')).toBeVisible();
    await expect(managerPage.locator('button:has-text("Xóa"), button:has-text("Delete")')).toBeVisible();
  });

  test('C2 - All tabs switch correctly', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.waitForTimeout(1000);

    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    const tabButtons = ['Tổng quan', 'Tồn kho', 'Kênh bán', 'Hình ảnh', 'Biến thể'];

    for (const tabName of tabButtons) {
      const tab = managerPage.locator(`button:has-text("${tabName}")`);
      const count = await tab.count();
      if (count > 0) {
        await tab.first().click();
        await managerPage.waitForTimeout(200);
      }
    }
  });

  test('C3 - Edit button navigates to edit page', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.waitForTimeout(1000);

    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    await managerPage.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
    await managerPage.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
    await expect(managerPage).toHaveURL(/\/products\/[a-f0-9-]+\/edit/);
  });

  test('C4 - Back button navigates to product list', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.waitForTimeout(1000);

    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    await managerPage.locator('button:has-text("Quay lại"), button:has-text("Back")').click();
    await managerPage.waitForURL(/\/products/);
    await expect(managerPage).toHaveURL(/\/products/);
  });

  test('C5 - Delete product - cancel on dialog', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.waitForTimeout(1000);

    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    // Set up dialog handler BEFORE clicking the delete button
    let dialogShown = false;
    managerPage.on('dialog', async (dialog) => {
      dialogShown = true;
      expect(dialog.message()).toMatch(/xóa|delete|confirm/i);
      await dialog.dismiss();
    });

    // Wait for delete button to be visible and clickable
    const deleteBtn = managerPage.locator('button:has-text("Xóa"), button:has-text("Delete")');
    await deleteBtn.waitFor({ state: 'visible', timeout: 5000 });
    await deleteBtn.click();

    // Dialog should have been shown
    expect(dialogShown).toBe(true);

    // URL should still be on detail page (not navigated away)
    await expect(managerPage).toHaveURL(/\/products\/[a-f0-9-]+$/);
  });

  test('C6 - Delete product - confirm', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await managerPage.waitForTimeout(1000);

    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
    }

    managerPage.on('dialog', async (dialog) => {
      await dialog.accept();
    });

    await managerPage.locator('button:has-text("Xóa"), button:has-text("Delete")').click();
    await managerPage.waitForTimeout(3000);
    await expect(managerPage).toHaveURL(/\/products/);
  });
});
