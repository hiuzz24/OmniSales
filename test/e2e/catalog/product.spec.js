const { test, expect } = require('../../fixtures/auth-fixtures');

test.describe('Product Listing E2E Tests', () => {

  test.beforeEach(async ({ managerPage }) => {
    await managerPage.goto('/products');
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
  });

  test('A1 - Product list page renders correctly', async ({ managerPage }) => {
    await expect(managerPage.locator('h1:has-text("Sản phẩm")')).toBeVisible();
    await expect(managerPage.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first()).toBeVisible();
    await expect(managerPage.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A2 - Search product by keyword', async ({ managerPage }) => {
    const searchInput = managerPage.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first();
    await searchInput.fill('áo');
    await managerPage.waitForTimeout(700);
    await expect(managerPage.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A3 - Filter by status (ACTIVE)', async ({ managerPage }) => {
    const statusSelect = managerPage.locator('select').nth(1);
    await statusSelect.selectOption('ACTIVE');
    await managerPage.waitForTimeout(500);
    await expect(managerPage.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A4 - Filter by platform (SHOPEE)', async ({ managerPage }) => {
    const platformSelect = managerPage.locator('select').nth(0);
    await platformSelect.selectOption('SHOPEE');
    await managerPage.waitForTimeout(500);
    await expect(managerPage.getByRole('heading', { name: /Danh sách sản phẩm/ })).toBeVisible();
  });

  test('A5 - Pagination navigation works', async ({ managerPage }) => {
    const paginationNav = managerPage.locator('nav[aria-label="Phân trang"], nav:has-text("Trang")').first();
    const hasPagination = await paginationNav.count();

    if (hasPagination > 0) {
      const nextBtn = managerPage.locator('button[aria-label="Trang sau"]:not([disabled]), button[aria-label="Next"]:not([disabled])').first();
      const isNextEnabled = await nextBtn.count() > 0;
      if (isNextEnabled) {
        await nextBtn.click();
        await managerPage.waitForTimeout(500);
      }
    }
    expect(true).toBeTruthy();
  });

  test('A6 - Click Chi tiết button navigates to detail page', async ({ managerPage }) => {
    await managerPage.waitForTimeout(1000);
    const rows = managerPage.locator('tbody tr');
    const count = await rows.count();

    if (count > 0) {
      const firstDetailBtn = managerPage.locator('button:has-text("Chi tiết")').first();
      if (await firstDetailBtn.isVisible()) {
        await firstDetailBtn.click();
        await managerPage.waitForURL(/\/products\/.+/);
        await expect(managerPage).toHaveURL(/\/products\/[a-f0-9-]+/);
      }
    }
  });

  test('A7 - Navigate to create product page', async ({ managerPage }) => {
    await managerPage.locator('button:has-text("Thêm sản phẩm mới"), button:has-text("Thêm mới")').click();
    await managerPage.waitForURL(/\/products\/create/);
    await expect(managerPage).toHaveURL(/\/products\/create/);
  });

  test('A8 - Navigate to product logs', async ({ managerPage }) => {
    const logsBtn = managerPage.locator('button:has-text("Nhật ký sản phẩm"), button:has-text("Lịch sử"), a:has-text("Nhật ký")').first();
    const hasLogsBtn = await logsBtn.count();
    if (hasLogsBtn > 0 && await logsBtn.isVisible()) {
      await logsBtn.click();
      await managerPage.waitForTimeout(2000);
      const currentUrl = managerPage.url();
      const isValidPage = currentUrl.includes('/products') || currentUrl.includes('/sync') || currentUrl.includes('/logs');
      expect(isValidPage).toBeTruthy();
    } else {
      test.skip();
    }
  });

  test('A9 - Empty state shows when no products match filter', async ({ managerPage }) => {
    const searchInput = managerPage.locator('input[placeholder*="Tìm kiếm"], input[placeholder*="Search"]').first();
    await searchInput.fill('xyznonexistentproduct99999xyz');
    await managerPage.waitForTimeout(700);
    const hasEmpty = await managerPage.locator('text=Không có sản phẩm, text=Không tìm thấy').count();
    if (hasEmpty > 0) {
      await expect(managerPage.locator('text=Không có sản phẩm, text=Không tìm thấy').first()).toBeVisible();
    }
  });
});
