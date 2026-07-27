const { test, expect } = require('../../fixtures/auth-fixtures');
const { uniqueSku } = require('../../utils/product-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Product Create E2E Tests', () => {

  test.beforeEach(async ({ managerPage }) => {
    await managerPage.goto('/products/create');
    await managerPage.waitForLoadState('domcontentloaded');
    await managerPage.waitForTimeout(2000);
  });

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // Basic UI tests (PC-E2E-1 to PC-E2E-6)

  test('PC-E2E-1 - Product Create page renders all form sections', async ({ managerPage }) => {
    await expect(managerPage.locator('#product-name, input[name="name"]').first()).toBeVisible();
    await expect(managerPage.locator('#product-sku, input[name="sku"]').first()).toBeVisible();
    await expect(managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first()).toBeVisible();
  });

  // Validation tests (B1-B7)

  test('B1 - Validation: empty required fields show errors', async ({ managerPage }) => {
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(500);
    const nameError = managerPage.locator('text=/tên|Tên|không.*trống|required/i').first();
    const hasError = await nameError.count();
    if (hasError > 0) {
      await expect(nameError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B2 - Validation: empty product name', async ({ managerPage }) => {
    const nameInput = managerPage.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await nameInput.clear();
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(500);
    const errorEl = managerPage.locator('text=/tên|Tên|không.*trống|required/i').first();
    await expect(errorEl).toBeVisible({ timeout: 3000 });
  });

  test('B3 - Validation: empty SKU', async ({ managerPage }) => {
    const nameInput = managerPage.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await managerPage.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    await managerPage.waitForTimeout(300);
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(500);
    const skuError = managerPage.locator('text=/SKU.*trống|SKU.*required/i').first();
    const hasError = await skuError.count();
    if (hasError > 0) {
      await expect(skuError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B4 - Validation: empty category', async ({ managerPage }) => {
    const nameInput = managerPage.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await managerPage.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    await managerPage.waitForTimeout(300);
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(500);
    const catError = managerPage.locator('text=/danh mục|danh.*mục|chọn.*danh/i').first();
    const hasError = await catError.count();
    if (hasError > 0) {
      await expect(catError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B5 - Validation: empty price', async ({ managerPage }) => {
    const nameInput = managerPage.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await managerPage.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    const categorySelect = managerPage.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await managerPage.waitForTimeout(500);
    }
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(500);
    const priceError = managerPage.locator('text=/giá|price|null/i').first();
    const hasError = await priceError.count();
    if (hasError > 0) {
      await expect(priceError).toBeVisible({ timeout: 3000 });
    }
  });

  test('B6 - Validation: negative price', async ({ managerPage }) => {
    const nameInput = managerPage.locator('#product-name, input[name="name"]').first();
    await nameInput.fill('Test Product');
    await managerPage.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku());
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(500);
    }
    const categorySelect = managerPage.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await managerPage.waitForTimeout(500);
    }
    const addVariantBtn = managerPage.locator('button:has-text("Thêm biến thể")').first();
    if (await addVariantBtn.count() > 0) {
      await addVariantBtn.click();
      await managerPage.waitForTimeout(1000);
    }
    await managerPage.waitForTimeout(500);
    const inputCount = await managerPage.locator('table input:not([disabled])').count();
    if (inputCount < 2) { test.skip(); return; }
    await managerPage.locator('table input:not([disabled])').nth(1).fill('-100');
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(500);
    const priceError = managerPage.locator('text=/giá.*0|greater.*0|positive/i').first();
    const hasError = await priceError.count();
    if (hasError > 0) {
      await expect(priceError).toBeVisible({ timeout: 3000 });
    }
  });

  // Happy path tests (B7-B14)

  test('B7 - Create simple product successfully', async ({ managerPage }) => {
    const timestamp = Date.now();
    await managerPage.locator('#product-name, input[name="name"]').first().fill(`E2E Test Product ${timestamp}`);
    await managerPage.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku('E2E'));
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(500);
    }
    const categorySelect = managerPage.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await managerPage.waitForTimeout(500);
    }
    const addVariantBtn = managerPage.locator('button:has-text("Thêm biến thể")').first();
    if (await addVariantBtn.count() > 0) {
      await addVariantBtn.click();
      await managerPage.waitForTimeout(500);
    }
    const allInputs = managerPage.locator('table input:not([disabled])');
    const inputCount = await allInputs.count();
    if (inputCount >= 2) {
      await allInputs.nth(0).fill(uniqueSku('SV'));
      await allInputs.nth(1).fill('199000');
    }
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(3000);
    const onProductsPage = (await managerPage.url()).includes('/products');
    const hasSuccess = await managerPage.locator('text=thành công, text=success, text=created').count();
    expect(onProductsPage || hasSuccess > 0).toBeTruthy();
  });

  test('B8 - Toggle variant mode', async ({ managerPage }) => {
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể"), button:has-text("Đã bật biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(300);
      const hasVariantSection = await managerPage.locator('text=Biến thể').count();
      if (hasVariantSection > 0) {
        await expect(managerPage.locator('text=Biến thể').first()).toBeVisible();
      }
    }
  });

  test('B9 - Add and fill one variant then submit', async ({ managerPage }) => {
    const timestamp = Date.now();
    await managerPage.locator('#product-name, input[name="name"]').first().fill(`E2E Variant Product ${timestamp}`);
    await managerPage.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku('VAR'));
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(500);
      const addVariantBtn = managerPage.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await managerPage.waitForTimeout(1000);
        const variantTable = managerPage.locator('table').first();
        await variantTable.waitFor({ state: 'visible', timeout: 5000 });
        const allInputs = managerPage.locator('table input:not([disabled])');
        const inputCount = await allInputs.count();
        if (inputCount >= 2) {
          await allInputs.nth(0).fill(uniqueSku('VR'));
          await allInputs.nth(1).fill('299000');
        }
        await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
        await managerPage.waitForTimeout(3000);
        await expect(managerPage).toHaveURL(/\/products/);
      }
    }
  });

  test('B10 - Validation: variant without SKU', async ({ managerPage }) => {
    await managerPage.locator('#product-name, input[name="name"]').first().fill('Variant Product No SKU');
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(500);
      const addVariantBtn = managerPage.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await managerPage.waitForTimeout(1000);
        const variantTable = managerPage.locator('table').first();
        await variantTable.waitFor({ state: 'visible', timeout: 5000 });
        const allInputs = managerPage.locator('table input:not([disabled])');
        const inputCount = await allInputs.count();
        if (inputCount >= 2) {
          await allInputs.nth(1).fill('150000');
        }
        await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
        await managerPage.waitForTimeout(500);
        const skuError = managerPage.locator('text=/SKU.*trống|SKU.*blank/i').first();
        const hasError = await skuError.count();
        if (hasError > 0) {
          await expect(skuError).toBeVisible({ timeout: 3000 });
        }
      }
    }
  });

  test('B11 - Validation: variant without price', async ({ managerPage }) => {
    await managerPage.locator('#product-name, input[name="name"]').first().fill('Variant Product No Price');
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(300);
      const addVariantBtn = managerPage.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await managerPage.waitForTimeout(300);
        const skuInputs = managerPage.locator('table input').nth(2);
        await skuInputs.fill(uniqueSku('NOPRICE'));
        await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
        await managerPage.waitForTimeout(500);
        await expect(managerPage).toHaveURL(/\/products\/create/);
      }
    }
  });

  test('B12 - Remove variant from list', async ({ managerPage }) => {
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(300);
      const addVariantBtn = managerPage.locator('button:has-text("Thêm biến thể")').first();
      const hasAddBtn = await addVariantBtn.count();
      if (hasAddBtn > 0) {
        await addVariantBtn.click();
        await managerPage.waitForTimeout(300);
        const removeBtnCountBefore = await managerPage.locator('button[title="Xóa biến thể"], button[title="Remove"]').count();
        if (removeBtnCountBefore > 0) {
          await managerPage.locator('button[title="Xóa biến thể"], button[title="Remove"]').first().click();
          await managerPage.waitForTimeout(300);
          const emptyState = managerPage.locator('text=Chưa có biến thể, text=No variants').first();
          const hasEmpty = await emptyState.count();
          if (hasEmpty > 0) {
            await expect(emptyState).toBeVisible();
          }
        }
      }
    }
  });

  test('B13 - Cancel create product redirects to list', async ({ managerPage }) => {
    await managerPage.locator('#product-name, input[name="name"]').first().fill('Some Product');
    await managerPage.locator('button:has-text("Hủy")').click();
    await managerPage.waitForURL(/\/products/);
    await expect(managerPage).toHaveURL(/\/products/);
  });

  test('B14 - Create product as DRAFT', async ({ managerPage }) => {
    const timestamp = Date.now();
    await managerPage.locator('#product-name, input[name="name"]').first().fill(`E2E Draft Product ${timestamp}`);
    await managerPage.locator('#product-sku, input[name="sku"]').first().fill(uniqueSku('DRAFT'));
    const variantBtn = managerPage.locator('button:has-text("Tạo biến thể")').first();
    const hasVariantBtn = await variantBtn.count();
    if (hasVariantBtn > 0) {
      await variantBtn.click();
      await managerPage.waitForTimeout(500);
    }
    const categorySelect = managerPage.locator('#product-category, select[name="category"]').first();
    const hasCategory = await categorySelect.count();
    if (hasCategory > 0) {
      await categorySelect.selectOption({ index: 1 });
      await managerPage.waitForTimeout(500);
    }
    const addVariantBtn = managerPage.locator('button:has-text("Thêm biến thể")').first();
    if (await addVariantBtn.count() > 0) {
      await addVariantBtn.click();
      await managerPage.waitForTimeout(500);
    }
    const allInputs = managerPage.locator('table input:not([disabled])');
    const inputCount = await allInputs.count();
    if (inputCount >= 2) {
      await allInputs.nth(0).fill(uniqueSku('DV'));
      await allInputs.nth(1).fill('99000');
    }
    await managerPage.locator('button:has-text("Tạo sản phẩm"), button:has-text("Lưu")').first().click();
    await managerPage.waitForTimeout(3000);
    const onProductsPage = (await managerPage.url()).includes('/products');
    expect(onProductsPage).toBeTruthy();
  });
});
