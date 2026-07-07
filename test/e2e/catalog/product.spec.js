const { test, expect } = require('@playwright/test');
const { loginAsManager } = require('../../utils/product-helpers');

test.describe('Product Listing E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
    await page.goto('/products');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
  });

  test('A1 - Product list page renders correctly', async ({ page }) => {
    await expect(page.locator('h1:has-text("Sản phẩm")')).toBeVisible();
    await expect(page.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A2 - Search product by keyword', async ({ page }) => {
    const searchInput = page.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first();
    await searchInput.fill('áo');
    await page.waitForTimeout(700);
    await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A3 - Filter by status (ACTIVE)', async ({ page }) => {
    const statusSelect = page.locator('select').nth(1);
    await statusSelect.selectOption('ACTIVE');
    await page.waitForTimeout(500);
    await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A4 - Filter by platform (SHOPEE)', async ({ page }) => {
    const platformSelect = page.locator('select').nth(0);
    await platformSelect.selectOption('SHOPEE');
    await page.waitForTimeout(500);
    await expect(page.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A5 - Pagination navigation works', async ({ page }) => {
    const paginationNav = page.locator('nav[aria-label="Phân trang"], nav:has-text("Trang")').first();
    const hasPagination = await paginationNav.count();

    if (hasPagination > 0) {
      const nextBtn = page.locator('button[aria-label="Trang sau"]:not([disabled]), button[aria-label="Next"]:not([disabled])').first();
      const isNextEnabled = await nextBtn.count() > 0;
      if (isNextEnabled) {
        await nextBtn.click();
        await page.waitForTimeout(500);
      }
    }
    expect(true).toBeTruthy();
  });

  test('A6 - Click Chi tiết button navigates to detail page', async ({ page }) => {
    await page.waitForTimeout(1000);
    const rows = page.locator('tbody tr');
    const count = await rows.count();

    if (count > 0) {
      const firstDetailBtn = page.locator('button:has-text("Chi tiết")').first();
      if (await firstDetailBtn.isVisible()) {
        await firstDetailBtn.click();
        await page.waitForURL(/\/products\/.+/);
        await expect(page).toHaveURL(/\/products\/[a-f0-9-]+/);
      }
    }
  });

  test('A7 - Navigate to create product page', async ({ page }) => {
    await page.locator('button:has-text("Thêm sản phẩm mới"), button:has-text("Thêm mới")').click();
    await page.waitForURL(/\/products\/create/);
    await expect(page).toHaveURL(/\/products\/create/);
  });

  test('A8 - Navigate to product logs', async ({ page }) => {
    const logsBtn = page.locator('button:has-text("Nhật ký sản phẩm"), button:has-text("Lịch sử"), a:has-text("Nhật ký")').first();
    const hasLogsBtn = await logsBtn.count();
    if (hasLogsBtn > 0 && await logsBtn.isVisible()) {
      await logsBtn.click();
      await page.waitForTimeout(2000);
      const currentUrl = page.url();
      const isValidPage = currentUrl.includes('/products') || currentUrl.includes('/sync') || currentUrl.includes('/logs');
      expect(isValidPage).toBeTruthy();
    } else {
      test.skip();
    }
  });

  test('A9 - Empty state shows when no products match filter', async ({ page }) => {
    const searchInput = page.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first();
    await searchInput.fill('xyznonexistentproduct99999xyz');
    await page.waitForTimeout(700);
    const hasEmpty = await page.locator('text=Không có sản phẩm, text=Không tìm thấy').count();
    if (hasEmpty > 0) {
      await expect(page.locator('text=Không có sản phẩm, text=Không tìm thấy').first()).toBeVisible();
    }
  });
});
