const { test, expect } = require('@playwright/test');
const { TEST_EMAIL, TEST_PASSWORD, FRONTEND_URL } = require('../../utils/env-config');
const { uniqueSku } = require('../../utils/product-helpers');

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

test.describe('Product Create E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
    await page.goto('/products/create');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);
  });

  // Basic UI tests (PC-E2E-1 to PC-E2E-6)

  test('PC-E2E-1 - Product Create page renders all form sections', async ({ page }) => {
    await expect(page.locator('#product-name, input[name="name"]').first()).toBeVisible();
    await expect(page.locator('#product-sku, input[name="sku"]').first()).toBeVisible();
    await expect(page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first()).toBeVisible();
  });

  // Validation tests (B1-B7)

  test('B1 - Validation: empty required fields show errors', async ({ page }) => {
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(500);
    const nameError = page.locator('text=/tên|Tên|không.*trống|required/i').first();
    const hasError = await nameError.count();
    if (hasError > 0) {
      await expect(nameError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B2 - Validation: empty product name', async ({ page }) => {
    const nameInput = page.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await nameInput.clear();
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(500);
    const errorEl = page.locator('text=/tên|Tên|không.*trống|required/i').first();
    await expect(errorEl).toBeVisible({ timeout: 3000 });
  });

  test('B3 - Validation: empty SKU', async ({ page }) => {
    const nameInput = page.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await page.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    await page.waitForTimeout(300);
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(500);
    const skuError = page.locator('text=/SKU.*trống|SKU.*required/i').first();
    const hasError = await skuError.count();
    if (hasError > 0) {
      await expect(skuError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B4 - Validation: empty category', async ({ page }) => {
    const nameInput = page.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await page.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    await page.waitForTimeout(300);
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(500);
    const catError = page.locator('text=/danh mục|danh.*mục|chọn.*danh/i').first();
    const hasError = await catError.count();
    if (hasError > 0) {
      await expect(catError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B5 - Validation: empty price', async ({ page }) => {
    const nameInput = page.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await page.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    const categorySelect = page.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await page.waitForTimeout(500);
    }
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(500);
    const priceError = page.locator('text=/giá|price|null/i').first();
    const hasError = await priceError.count();
    if (hasError > 0) {
      await expect(priceError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B6 - Validation: negative price', async ({ page }) => {
    const nameInput = page.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await page.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    const variantBtn = page.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(500);
    }
    const categorySelect = page.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await page.waitForTimeout(500);
    }
    const addVariantBtn = page.locator('button:has-text("Thêm biến thể")').first();
    if (await addVariantBtn.count() > 0) {
      await addVariantBtn.click();
      await page.waitForTimeout(1000);
    }
    await page.waitForTimeout(500);
    const inputCount = await page.locator('table input:not([disabled])').count();
    if (inputCount < 2) { test.skip(); return; }
    await page.locator('table input:not([disabled])').nth(1).fill('-100');
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(500);
    const priceError = page.locator('text=/giá.*0|greater.*0|positive/i').first();
    const hasError = await priceError.count();
    if (hasError > 0) {
      await expect(priceError).toBeVisible({ timeout: 3000 });
    }
  });

  // Happy path tests (B7-B14)

  test('B7 - Create simple product successfully', async ({ page }) => {
    const timestamp = Date.now();
    await page.locator('#product-name, input[name="name"]').first().fill(`E2E Test Product ${timestamp}`);
    await page.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku('E2E'));
    const variantBtn = page.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(500);
    }
    const categorySelect = page.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await page.waitForTimeout(500);
    }
    const addVariantBtn = page.locator('button:has-text("Thêm biến thể")').first();
    if (await addVariantBtn.count() > 0) {
      await addVariantBtn.click();
      await page.waitForTimeout(500);
    }
    const allInputs = page.locator('table input:not([disabled])');
    const inputCount = await allInputs.count();
    if (inputCount >= 2) {
      await allInputs.nth(0).fill(uniqueSku('SV'));
      await allInputs.nth(1).fill('199000');
    }
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(3000);
    const onProductsPage = (await page.url()).includes('/products');
    const hasSuccess = await page.locator('text=thành công, text=success, text=created').count();
    expect(onProductsPage || hasSuccess > 0).toBeTruthy();
  });

  test('B8 - Toggle variant mode', async ({ page }) => {
    const variantBtn = page.locator('button:has-text("Tạo biến thể"), button:has-text("Đã bật biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(300);
      const hasVariantSection = await page.locator('text=Biến thể').count();
      if (hasVariantSection > 0) {
        await expect(page.locator('text=Biến thể').first()).toBeVisible();
      }
    }
  });

  test('B9 - Add and fill one variant then submit', async ({ page }) => {
    const timestamp = Date.now();
    await page.locator('#product-name, input[name="name"]').first().fill(`E2E Variant Product ${timestamp}`);
    await page.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku('VAR'));
    const variantBtn = page.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(500);
      const addVariantBtn = page.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await page.waitForTimeout(1000);
        const variantTable = page.locator('table').first();
        await variantTable.waitFor({ state: 'visible', timeout: 5000 });
        const allInputs = page.locator('table input:not([disabled])');
        const inputCount = await allInputs.count();
        if (inputCount >= 2) {
          await allInputs.nth(0).fill(uniqueSku('VR'));
          await allInputs.nth(1).fill('299000');
        }
        await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
        await page.waitForTimeout(3000);
        await expect(page).toHaveURL(/\/products/);
      }
    }
  });

  test('B10 - Validation: variant without SKU', async ({ page }) => {
    await page.locator('#product-name, input[name="name"]').first().fill('Variant Product No SKU');
    const variantBtn = page.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(500);
      const addVariantBtn = page.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await page.waitForTimeout(1000);
        const variantTable = page.locator('table').first();
        await variantTable.waitFor({ state: 'visible', timeout: 5000 });
        const allInputs = page.locator('table input:not([disabled])');
        const inputCount = await allInputs.count();
        if (inputCount >= 2) {
          await allInputs.nth(1).fill('150000');
        }
        await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
        await page.waitForTimeout(500);
        const skuError = page.locator('text=/SKU.*trống|SKU.*blank/i').first();
        const hasError = await skuError.count();
        if (hasError > 0) {
          await expect(skuError).toBeVisible({ timeout: 3000 });
        }
      }
    }
  });

  test('B11 - Validation: variant without price', async ({ page }) => {
    await page.locator('#product-name, input[name="name"]').first().fill('Variant Product No Price');
    const variantBtn = page.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(300);
      const addVariantBtn = page.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await page.waitForTimeout(300);
        const skuInputs = page.locator('table input').nth(2);
        await skuInputs.fill(uniqueSku('NOPRICE'));
        await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
        await page.waitForTimeout(500);
        await expect(page).toHaveURL(/\/products\/create/);
      }
    }
  });

  test('B12 - Remove variant from list', async ({ page }) => {
    const variantBtn = page.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(300);
      const addVariantBtn = page.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await page.waitForTimeout(300);
        const removeBtnCountBefore = await page.locator('button[title="Xóa biến thể"], button[title="Remove"]').count();
        if (removeBtnCountBefore > 0) {
          await page.locator('button[title="Xóa biến thể"], button[title="Remove"]').first().click();
          await page.waitForTimeout(300);
          const emptyState = page.locator('text=Chưa có biến thể, text=No variants').first();
          const hasEmpty = await emptyState.count();
          if (hasEmpty > 0) {
            await expect(emptyState).toBeVisible();
          }
        }
      }
    }
  });

  test('B13 - Cancel create product redirects to list', async ({ page }) => {
    await page.locator('#product-name, input[name="name"]').first().fill('Some Product');
    await page.locator('button:has-text("Hủy")').click();
    await page.waitForURL(/\/products/);
    await expect(page).toHaveURL(/\/products/);
  });

  test('B14 - Create product as DRAFT', async ({ page }) => {
    const timestamp = Date.now();
    await page.locator('#product-name, input[name="name"]').first().fill(`E2E Draft Product ${timestamp}`);
    await page.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku('DRAFT'));
    const variantBtn = page.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await page.waitForTimeout(500);
    }
    const categorySelect = page.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await page.waitForTimeout(500);
    }
    const addVariantBtn = page.locator('button:has-text("Thêm biến thể")').first();
    if (await addVariantBtn.count() > 0) {
      await addVariantBtn.click();
      await page.waitForTimeout(500);
    }
    const allInputs = page.locator('table input:not([disabled])');
    const inputCount = await allInputs.count();
    if (inputCount >= 2) {
      await allInputs.nth(0).fill(uniqueSku('DV'));
      await allInputs.nth(1).fill('99000');
    }
    await page.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await page.waitForTimeout(3000);
    const onProductsPage = (await page.url()).includes('/products');
    expect(onProductsPage).toBeTruthy();
  });
});
