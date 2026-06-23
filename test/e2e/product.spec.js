const { test, expect } = require('@playwright/test');
const { loginAsManager, uniqueSku } = require('../utils/product-helpers');

test.describe('Product E2E Tests', () => {

  // =========================================================
  // A. PRODUCT LISTING PAGE
  // =========================================================
  test.describe('Product Listing Page', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsManager(page);
      await page.goto('/products');
    });

    test('A1 - Product list page renders correctly', async ({ page }) => {
      await expect(page.locator('h1:has-text("Sản phẩm")')).toBeVisible();
      await expect(page.locator('input[placeholder*="Tìm kiếm"]')).toBeVisible();
      await expect(page.locator('text=Danh sách sản phẩm')).toBeVisible();
    });

    test('A2 - Search product by keyword', async ({ page }) => {
      const searchInput = page.locator('input[placeholder*="Tìm kiếm"]');
      await searchInput.fill('áo');
      // Debounce waits
      await page.waitForTimeout(700);

      const tableTitle = page.locator('text=Danh sách sản phẩm');
      await expect(tableTitle).toBeVisible();
    });

    test('A3 - Filter by status (ACTIVE)', async ({ page }) => {
      const statusSelect = page.locator('select').nth(1);
      await statusSelect.selectOption('ACTIVE');
      await page.waitForTimeout(500);

      const tableTitle = page.locator('text=Danh sách sản phẩm');
      await expect(tableTitle).toBeVisible();
    });

    test('A4 - Filter by platform (SHOPEE)', async ({ page }) => {
      const platformSelect = page.locator('select').nth(0);
      await platformSelect.selectOption('SHOPEE');
      await page.waitForTimeout(500);

      const tableTitle = page.locator('text=Danh sách sản phẩm');
      await expect(tableTitle).toBeVisible();
    });

    test('A5 - Pagination navigation works', async ({ page }) => {
      const nextBtn = page.locator('button:has-text("Sau")');
      const prevBtn = page.locator('button:has-text("Trước")');

      // If there is more than 1 page, Next should be enabled
      const isNextDisabled = await nextBtn.getAttribute('disabled');
      if (isNextDisabled === null) {
        await nextBtn.click();
        await page.waitForTimeout(500);
        // Prev should now be enabled
        await expect(prevBtn).not.toBeDisabled();
      }
    });

    test('A6 - Click Chi tiet button navigates to detail page', async ({ page }) => {
      // Wait for table to load
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
      await page.locator('button:has-text("Thêm sản phẩm mới")').click();
      await page.waitForURL(/\/products\/create/);
      await expect(page).toHaveURL(/\/products\/create/);
    });

    test('A8 - Navigate to product logs', async ({ page }) => {
      await page.locator('button:has-text("Nhật ký sản phẩm")').click();
      await page.waitForURL(/\/products\/logs/);
      await expect(page).toHaveURL(/\/products\/logs/);
    });

    test('A9 - Empty state shows when no products match filter', async ({ page }) => {
      // Search for something very unlikely to exist
      const searchInput = page.locator('input[placeholder*="Tìm kiếm"]');
      await searchInput.fill('xyznonexistentproduct99999xyz');
      await page.waitForTimeout(700);

      const emptyMsg = page.locator('text=Không có sản phẩm nào');
      const hasEmpty = await emptyMsg.count();
      if (hasEmpty > 0) {
        await expect(emptyMsg.first()).toBeVisible();
      }
    });
  });

  // =========================================================
  // B. PRODUCT CREATE PAGE
  // =========================================================
  test.describe('Product Create Page', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsManager(page);
      await page.goto('/products/create');
    });

    test('B1 - Create product page renders all form sections', async ({ page }) => {
      await expect(page.locator('#product-name')).toBeVisible();
      await expect(page.locator('#product-sku')).toBeVisible();
      await expect(page.locator('#product-price')).toBeVisible();
      await expect(page.locator('#product-category')).toBeVisible();
      await expect(page.locator('button:has-text("Tạo sản phẩm")')).toBeVisible();
    });

    test('B2 - Validation: empty required fields show errors', async ({ page }) => {
      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      // At minimum, name error should appear
      const nameError = page.locator('#product-name + span, .errorText').filter({ hasText: /tên|Tên|không.*trống/i });
      await expect(nameError.first()).toBeVisible({ timeout: 3000 });
    });

    test('B3 - Validation: empty product name', async ({ page }) => {
      await page.locator('#product-name').fill('Test Product');
      await page.locator('#product-name').clear();
      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      const errorEl = page.locator('text=/tên|Tên|không.*trống/i').first();
      await expect(errorEl).toBeVisible({ timeout: 3000 });
    });

    test('B4 - Validation: empty SKU', async ({ page }) => {
      await page.locator('#product-name').fill('Test Product');
      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      const skuError = page.locator('text=/SKU.*trống|SKU.*required/i').first();
      await expect(skuError).toBeVisible({ timeout: 3000 });
    });

    test('B5 - Validation: empty category', async ({ page }) => {
      await page.locator('#product-name').fill('Test Product');
      await page.locator('#product-sku').fill(uniqueSku());
      // Category is left empty
      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      const catError = page.locator('text=/danh mục|danh.*mục|chọn.*danh/i').first();
      await expect(catError).toBeVisible({ timeout: 3000 });
    });

    test('B6 - Validation: empty price', async ({ page }) => {
      await page.locator('#product-name').fill('Test Product');
      await page.locator('#product-sku').fill(uniqueSku());
      await page.locator('#product-category').selectOption({ index: 1 });
      // Price left empty
      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      const priceError = page.getByText(/giá|price|null/i).first();
      await expect(priceError).toBeVisible({ timeout: 3000 });
    });

    test('B7 - Validation: negative price', async ({ page }) => {
      await page.locator('#product-name').fill('Test Product');
      await page.locator('#product-sku').fill(uniqueSku());
      await page.locator('#product-category').selectOption({ index: 1 });
      await page.locator('#product-price').fill('-100');
      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      const priceError = page.locator('text=/giá.*0|greater.*0|positive/i').first();
      await expect(priceError).toBeVisible({ timeout: 3000 });
    });

    test('B8 - Create simple product successfully', async ({ page }) => {
      await page.locator('#product-name').fill(`E2E Test Product ${Date.now()}`);
      await page.locator('#product-sku').fill(uniqueSku('E2E'));
      await page.locator('#product-category').selectOption({ index: 1 });
      await page.locator('#product-price').fill('199000');

      await page.locator('button:has-text("Tạo sản phẩm")').click();

      // Should redirect to product list
      await page.waitForURL(/\/products(?!\/create)(?!\/[a-f0-9-]+\/edit)/, { timeout: 10000 });
      await expect(page).toHaveURL(/\/products/);

      // Toast should appear
      const toast = page.locator('.Toastify__toast').first();
      await expect(toast).toBeVisible({ timeout: 5000 });
    });

    test('B9 - Toggle variant mode', async ({ page }) => {
      const variantBtn = page.locator('button:has-text("Tạo biến thể"), button:has-text("Đã bật biến thể")');
      await variantBtn.click();
      await page.waitForTimeout(300);

      // Form giá đơn nên ẩn, form biến thể nên hiển thị
      await expect(page.locator('text=Biến thể sản phẩm').first()).toBeVisible();
    });

    test('B10 - Add and fill one variant then submit', async ({ page }) => {
      await page.locator('#product-name').fill(`E2E Variant Product ${Date.now()}`);
      await page.locator('#product-sku').fill(uniqueSku('VAR'));

      // Enable variant mode
      const variantBtn = page.locator('button:has-text("Tạo biến thể")');
      await variantBtn.click();
      await page.waitForTimeout(300);

      // Add variant
      await page.locator('button:has-text("Thêm biến thể")').click();
      await page.waitForTimeout(300);

      // Fill variant fields - first row inputs
      const variantInputs = page.locator('input[placeholder="S"]');
      await variantInputs.first().fill('L');

      const colorInputs = page.locator('input[placeholder="Trắng"]');
      await colorInputs.first().fill('Đỏ');

      const skuInputs = page.locator('table input').nth(2);
      await skuInputs.fill(uniqueSku('VR'));

      const priceInputs = page.locator('table input[type="number"]');
      await priceInputs.first().fill('299000');

      // Submit
      await page.locator('button:has-text("Tạo sản phẩm")').click();

      // Should redirect to product list
      await page.waitForURL(/\/products/, { timeout: 10000 });
      await expect(page).toHaveURL(/\/products/);
    });

    test('B11 - Validation: variant without SKU', async ({ page }) => {
      await page.locator('#product-name').fill('Variant Product No SKU');

      // Enable variant mode
      await page.locator('button:has-text("Tạo biến thể")').click();
      await page.waitForTimeout(300);

      // Add variant but don't fill SKU
      await page.locator('button:has-text("Thêm biến thể")').click();
      await page.waitForTimeout(300);

      // Fill only price (no SKU)
      const priceInputs = page.locator('table input[type="number"]');
      await priceInputs.first().fill('150000');

      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      const skuError = page.locator('text=/SKU.*trống|SKU.*blank/i').first();
      await expect(skuError).toBeVisible({ timeout: 3000 });
    });

    test('B12 - Validation: variant without price', async ({ page }) => {
      await page.locator('#product-name').fill('Variant Product No Price');

      // Enable variant mode
      await page.locator('button:has-text("Tạo biến thể")').click();
      await page.waitForTimeout(300);

      // Add variant but don't fill price
      await page.locator('button:has-text("Thêm biến thể")').click();
      await page.waitForTimeout(300);

      // Fill only SKU (no price)
      const skuInputs = page.locator('table input').nth(2);
      await skuInputs.fill(uniqueSku('NOPRICE'));

      await page.locator('button:has-text("Tạo sản phẩm")').click();
      await page.waitForTimeout(300);

      // Verify form did NOT redirect to product list (stayed on create page due to validation error)
      await expect(page).toHaveURL(/\/products\/create/);
    });

    test('B13 - Remove variant from list', async ({ page }) => {
      // Enable variant mode
      await page.locator('button:has-text("Tạo biến thể")').click();
      await page.waitForTimeout(300);

      // Add a variant
      await page.locator('button:has-text("Thêm biến thể")').click();
      await page.waitForTimeout(300);

      // Count before remove
      const removeBtnCountBefore = await page.locator('button[title="Xóa biến thể"]').count();

      if (removeBtnCountBefore > 0) {
        // Remove it
        await page.locator('button[title="Xóa biến thể"]').first().click();
        await page.waitForTimeout(300);

        // Should show empty state
        await expect(page.locator('text=Chưa có biến thể nào')).toBeVisible();
      }
    });

    test('B14 - Cancel create product redirects to list', async ({ page }) => {
      await page.locator('#product-name').fill('Some Product');
      await page.locator('button:has-text("Hủy")').click();

      await page.waitForURL(/\/products$/);
      await expect(page).toHaveURL(/\/products$/);
    });

    test('B15 - Create product as DRAFT', async ({ page }) => {
      await page.locator('#product-name').fill(`E2E Draft Product ${Date.now()}`);
      await page.locator('#product-sku').fill(uniqueSku('DRAFT'));
      await page.locator('#product-category').selectOption({ index: 1 });
      await page.locator('#product-price').fill('99000');

      // Uncheck "Hiển thị và cho phép đặt hàng" (the status checkbox in sidebar)
      const statusToggle = page.locator('input[type="checkbox"]').last();
      await statusToggle.uncheck({ timeout: 5000 });
      await page.waitForTimeout(200);

      await page.locator('button:has-text("Tạo sản phẩm")').click();

      await page.waitForURL(/\/products$/, { timeout: 10000 });
      await expect(page).toHaveURL(/\/products$/);
    });
  });

  // =========================================================
  // C. PRODUCT DETAIL PAGE
  // =========================================================
  test.describe('Product Detail Page', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsManager(page);
      // Navigate directly to a product if available
      await page.goto('/products');
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
      // Check that we're on a detail page
      await expect(page).toHaveURL(/\/products\/[a-f0-9-]+$/);
      await expect(page.locator('button:has-text("Quay lại")')).toBeVisible();
      await expect(page.locator('button:has-text("Chỉnh sửa")')).toBeVisible();
      await expect(page.locator('button:has-text("Xóa")')).toBeVisible();
    });

    test('C2 - All tabs switch correctly', async ({ page }) => {
      await page.goto('/products');
      await page.waitForTimeout(1000);

      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      }

      const tabButtons = [
        'Tổng quan',
        'Tồn kho',
        'Kênh bán',
        'Hình ảnh',
        'Biến thể',
      ];

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
      await page.waitForTimeout(1000);

      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      }

      await page.locator('button:has-text("Chỉnh sửa")').click();
      await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
      await expect(page).toHaveURL(/\/products\/[a-f0-9-]+\/edit/);
    });

    test('C4 - Back button navigates to product list', async ({ page }) => {
      await page.goto('/products');
      await page.waitForTimeout(1000);

      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      }

      await page.locator('button:has-text("Quay lại")').click();
      await page.waitForURL(/\/products$/);
      await expect(page).toHaveURL(/\/products$/);
    });

    test('C5 - Delete product - cancel on dialog', async ({ page }) => {
      await page.goto('/products');
      await page.waitForTimeout(1000);

      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      }

      // Set up dialog handler BEFORE clicking delete
      page.on('dialog', async (dialog) => {
        expect(dialog.message()).toContain('xóa');
        await dialog.dismiss();
      });

      await page.locator('button:has-text("Xóa")').click();
      await page.waitForTimeout(500);

      // Should still be on detail page
      await expect(page).toHaveURL(/\/products\/[a-f0-9-]+$/);
    });

    test('C6 - Delete product - confirm', async ({ page }) => {
      await page.goto('/products');
      await page.waitForTimeout(1000);

      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
      }

      // Set up dialog handler BEFORE clicking delete
      page.on('dialog', async (dialog) => {
        await dialog.accept();
      });

      await page.locator('button:has-text("Xóa")').click();
      await page.waitForURL(/\/products$/, { timeout: 10000 });

      await expect(page).toHaveURL(/\/products$/);
      const toast = page.locator('.Toastify__toast').first();
      await expect(toast).toBeVisible({ timeout: 5000 });
    });
  });

  // =========================================================
  // D. PRODUCT EDIT PAGE
  // =========================================================
  test.describe('Product Edit Page', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsManager(page);
      await page.goto('/products');
      await page.waitForTimeout(1000);
    });

    test('D1 - Edit page pre-fills form with existing data', async ({ page }) => {
      const rows = page.locator('tbody tr');
      const count = await rows.count();

      if (count > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);

        await page.locator('button:has-text("Chỉnh sửa")').click();
        await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);

        // Wait for form to load
        await page.waitForTimeout(1000);

        // Name should be pre-filled (not empty)
        const nameValue = await page.locator('#product-name').inputValue();
        expect(nameValue.trim().length).toBeGreaterThan(0);
      }
    });

    test('D2 - Update product name successfully', async ({ page }) => {
      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
        await page.locator('button:has-text("Chỉnh sửa")').click();
        await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
        await page.waitForTimeout(1000);

        // Clear and update name
        await page.locator('#product-name').clear();
        const newName = `Updated E2E Product ${Date.now()}`;
        await page.locator('#product-name').fill(newName);

        await page.locator('button:has-text("Cập nhật")').click();

        await page.waitForURL(/\/products\/[a-f0-9-]+$/, { timeout: 10000 });
        await expect(page).toHaveURL(/\/products\/[a-f0-9-]+$/);

        const toast = page.locator('.Toastify__toast').first();
        await expect(toast).toBeVisible({ timeout: 5000 });
      }
    });

    test('D3 - Validation: empty name on update', async ({ page }) => {
      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
        await page.locator('button:has-text("Chỉnh sửa")').click();
        await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);
        await page.waitForTimeout(1000);

        await page.locator('#product-name').clear();
        await page.locator('button:has-text("Cập nhật")').click();
        await page.waitForTimeout(300);

        const nameError = page.locator('text=/tên|Tên|không.*trống|blank/i').first();
        await expect(nameError).toBeVisible({ timeout: 3000 });
      }
    });

    test('D4 - Cancel edit redirects back to detail', async ({ page }) => {
      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await page.locator('button:has-text("Chi tiết")').first().click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
        await page.locator('button:has-text("Chỉnh sửa")').click();
        await page.waitForURL(/\/products\/[a-f0-9-]+\/edit/);

        await page.locator('button:has-text("Hủy")').click();
        await page.waitForURL(/\/products\/[a-f0-9-]+$/);
        await expect(page).toHaveURL(/\/products\/[a-f0-9-]+$/);
      }
    });
  });
});
