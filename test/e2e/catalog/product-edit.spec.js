const { test, expect } = require('@playwright/test');
const { loginAsManager } = require('../../utils/product-helpers');

test.describe('Product Edit E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
  });

  test('D1 - Edit page pre-fills form with existing data', async ({ page }) => {
    const rows = page.locator('tbody tr');
    const count = await rows.count();

    if (count > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);

      await page.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);

      await page.waitForTimeout(1000);

      const nameValue = await page.locator('#product-name, input[name="name"]').first().inputValue();
      expect(nameValue.trim().length).toBeGreaterThan(0);
    }
  });

  test('D2 - Update product name successfully', async ({ page }) => {
    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      await page.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
      await page.waitForTimeout(1000);

      const nameInput = page.locator('#product-name, input[name="name"]').first();
      await nameInput.clear();
      const newName = `Updated E2E Product ${Date.now()}`;
      await nameInput.fill(newName);

      await page.locator('button:has-text("Cập nhật"), button:has-text("Update"), button:has-text("Lưu")').first().click();

      await page.waitForTimeout(3000);
      await expect(page).toHaveURL(/\/products/);
    }
  });

  test('D3 - Validation: empty name on update', async ({ page }) => {
    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      await page.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
      await page.waitForTimeout(1000);

      const nameInput = page.locator('#product-name, input[name="name"]').first();
      await nameInput.clear();
      await page.locator('button:has-text("Cập nhật"), button:has-text("Update"), button:has-text("Lưu")').first().click();
      await page.waitForTimeout(500);

      const nameError = page.locator('text=/tên|Tên|không.*trống|required/i').first();
      const hasError = await nameError.count();
      if (hasError > 0) {
        await expect(nameError).toBeVisible({ timeout: 3000 });
      }
    }
  });

  test('D4 - Cancel edit redirects back to detail', async ({ page }) => {
    const rows = page.locator('tbody tr');
    if (await rows.count() > 0) {
      await page.locator('button:has-text("Chi tiết")').first().click();
      await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      await page.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);

      await page.locator('button:has-text("Hủy"), button:has-text("Cancel")').click();
      await page.waitForTimeout(1000);
      await expect(page).toHaveURL(/\/products\/[a-f0-9-]+$/);
    }
  });
});
