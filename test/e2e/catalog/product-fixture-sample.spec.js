/**
 * Sample E2E spec demonstrating the `managerPage` fixture.
 *
 * Compare with the legacy pattern:
 *   const { test, expect } = require('@playwright/test');
 *   const { loginAsManager } = require('../../utils/product-helpers');
 *   test.beforeEach(async ({ page }) => { await loginAsManager(page); });
 *
 * New pattern (this file):
 *   const { test, expect } = require('../../fixtures/auth-fixtures');
 *   // managerPage is already logged in.
 */

const { test, expect } = require('../../fixtures/auth-fixtures');

test.describe('Product Listing E2E Tests (fixture-based)', () => {

  test('FIX-A1 - Product list page renders correctly', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage.locator('h1:has-text("Sản phẩm")')).toBeVisible();
    await expect(managerPage.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first()).toBeVisible();
    await expect(managerPage.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('FIX-A2 - Search product by keyword', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first();
    await searchInput.fill('áo');
    await managerPage.waitForTimeout(700);
    await expect(managerPage.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('FIX-A3 - Filter by status (ACTIVE)', async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    // The product filter bar exposes a single <select> for status. There is
    // no other select on the page, so use the status option text directly.
    const statusSelect = managerPage.locator('select').first();
    await statusSelect.selectOption('ACTIVE');
    await managerPage.waitForTimeout(500);
    await expect(managerPage.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });
});
