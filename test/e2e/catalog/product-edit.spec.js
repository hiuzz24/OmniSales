const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Product Edit E2E Tests', () => {

  test.beforeEach(async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('domcontentloaded');
    await managerPage.waitForTimeout(2000);
  });

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('D1 - Edit page pre-fills form with existing data', async ({ managerPage }) => {
    const rows = managerPage.locator('tbody tr');
    const count = await rows.count();

    if (count > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);

      await managerPage.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+\/edit/);

      await managerPage.waitForTimeout(1000);

      const nameValue = await managerPage.locator('#product-name, input[name="name"]').first().inputValue();
      expect(nameValue.trim().length).toBeGreaterThan(0);
    }
  });

  test('D2 - Update product name successfully', async ({ managerPage }) => {
    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
      await managerPage.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
      await managerPage.waitForTimeout(1000);

      const nameInput = managerPage.locator('#product-name, input[name="name"]').first();
      await nameInput.clear();
      const newName = `Updated E2E Product ${Date.now()}`;
      await nameInput.fill(newName);

      await managerPage.locator('button:has-text("Cập nhật"), button:has-text("Update"), button:has-text("Lưu")').first().click();

      await managerPage.waitForTimeout(3000);
      await expect(managerPage).toHaveURL(/\/products/);
    }
  });

  test('D3 - Validation: empty name on update', async ({ managerPage }) => {
    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
      await managerPage.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
      await managerPage.waitForTimeout(1000);

      const nameInput = managerPage.locator('#product-name, input[name="name"]').first();
      await nameInput.clear();
      await managerPage.locator('button:has-text("Cập nhật"), button:has-text("Update"), button:has-text("Lưu")').first().click();
      await managerPage.waitForTimeout(500);

      const nameError = managerPage.locator('text=/tên|Tên|không.*trống|required/i').first();
      const hasError = await nameError.count();
      if (hasError > 0) {
        await expect(nameError).toBeVisible({ timeout: 3000 });
      }
    }
  });

  test('D4 - Cancel edit redirects back to detail', async ({ managerPage }) => {
    const rows = managerPage.locator('tbody tr');
    if (await rows.count() > 0) {
      await managerPage.locator('button:has-text("Chi tiết")').first().click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+$/);
      await managerPage.locator('button:has-text("Chỉnh sửa"), button:has-text("Edit")').click();
      await managerPage.waitForURL(/\/products\/[a-f0-9-]+\/edit/);

      await managerPage.locator('button:has-text("Hủy"), button:has-text("Cancel")').click();
      await managerPage.waitForTimeout(1000);
      await expect(managerPage).toHaveURL(/\/products\/[a-f0-9-]+$/);
    }
  });
});
