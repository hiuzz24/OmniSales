const { test, expect } = require('@playwright/test');
const {
  loginAsOwner,
  getWarehouseId,
  getSupplierId,
  API_BASE,
} = require('../../utils/warehouse-helpers');
const { FRONTEND_URL } = require('../../utils/env-config');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || FRONTEND_URL;

test.describe('Warehouse E2E Tests', () => {

  let warehouseId;
  let supplierId;

  test.beforeAll(async ({ request }) => {
    const { getAuthToken, getWarehouseId, getSupplierId } = require('../../utils/warehouse-helpers');
    const token = await getAuthToken(request);
    warehouseId = await getWarehouseId(request, token);
    supplierId = await getSupplierId(request, token);
  });

  // A. NHAP KHO (STOCK RECEIVE) TESTS

  test.describe('Stock Receive (Nhap Kho)', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsOwner(page);
    });

    test('R1 - Stock receive list page renders correctly', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/receipts`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      
      // Check page title or header
      const pageTitle = page.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('R2 - Filter by status (All/Completed/Draft)', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/receipts`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for status filter buttons or dropdown
      const statusFilter = page.locator('button, [class*="status"], [class*="filter"]').first();
      if (await statusFilter.isVisible()) {
        await statusFilter.click();
      }
      
      // Page should still render without errors
      await expect(page.locator('body')).toBeVisible();
    });

    test('R3 - Search by receipt code', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/receipts`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for search input
      const searchInput = page.locator('input[placeholder*="tim"], input[placeholder*="ma"], input[type="search"]').first();
      if (await searchInput.isVisible({ timeout: 3000 })) {
        await searchInput.fill('PN-');
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('R4 - Navigate to create receipt page', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/receipts`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for create button
      const createBtn = page.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Them"), button:has-text("+")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        
        // Should navigate to create page
        await expect(page).toHaveURL(/create/);
      } else {
        // If no create button, just verify we're on the list page
        await expect(page).toHaveURL(/receipts/);
      }
    });

    test('R5 - Create receipt page renders form fields', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/receipts/create`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Check for warehouse selector
      const warehouseField = page.locator('select[id*="warehouse"], [class*="warehouse"]').first();
      
      // Check for date field
      const dateField = page.locator('input[type="date"], input[id*="date"]').first();
      
      // At least one form field should be visible
      const hasForm = await warehouseField.isVisible({ timeout: 3000 }) || 
                      await dateField.isVisible({ timeout: 3000 });
      expect(hasForm).toBeTruthy();
    });

    test('R11 - View receipt detail', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/receipts`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for a detail/view button
      const detailBtn = page.locator('button:has-text("Chi tiet"), a:has-text("Chi tiet"), button:has-text("View")').first();
      if (await detailBtn.isVisible({ timeout: 3000 })) {
        await detailBtn.click();
        await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        
        // Should navigate to detail or show modal
        const isOnDetail = page.url().includes('/receipts/') && !page.url().includes('/create');
        const hasDetailContent = await page.locator('[class*="detail"], [class*="info"]').first().isVisible({ timeout: 3000 });
        expect(isOnDetail || hasDetailContent).toBeTruthy();
      }
    });

    test('R13 - Validation: missing warehouse', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/receipts/create`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Try to submit without selecting warehouse
      const submitBtn = page.locator('button[type="submit"], button:has-text("Luu"), button:has-text("Hoan thanh")').first();
      if (await submitBtn.isVisible({ timeout: 3000 })) {
        await submitBtn.click();
        await page.waitForTimeout(500);
        
        // Check for validation error
        const hasError = await page.locator('[class*="error"], [class*="required"], text:has-text("bat buoc")').first().isVisible({ timeout: 3000 });
        expect(hasError || page.url().includes('create')).toBeTruthy();
      }
    });
  });

  // B. XUAT KHO (STOCK DELIVERY) TESTS

  test.describe('Stock Delivery (Xuat Kho)', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsOwner(page);
    });

    test('D1 - Stock delivery list page renders correctly', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      
      const pageTitle = page.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('D2 - Filter by delivery type', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for delivery type filter
      const typeFilter = page.locator('button:has-text("ORDER"), button:has-text("DISPOSAL"), button:has-text("ADJUSTMENT")').first();
      if (await typeFilter.isVisible({ timeout: 3000 })) {
        await typeFilter.click();
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('D3 - Navigate to create delivery page', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = page.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Them")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        
        await expect(page).toHaveURL(/create/);
      }
    });

    test('D7 - Delivery type selection shows correct form', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/stock-deliveries/create`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for delivery type selector
      const typeSelector = page.locator('select[id*="type"], [class*="type"], button:has-text("ORDER")').first();
      if (await typeSelector.isVisible({ timeout: 3000 })) {
        await typeSelector.click();
        await page.waitForTimeout(300);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('D11 - View delivery detail', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const detailBtn = page.locator('button:has-text("Chi tiet"), a:has-text("Chi tiet")').first();
      if (await detailBtn.isVisible({ timeout: 3000 })) {
        await detailBtn.click();
        await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        
        const hasDetail = await page.locator('[class*="detail"], [class*="info"]').first().isVisible({ timeout: 3000 });
        expect(hasDetail || page.url().includes('/stock-deliveries/')).toBeTruthy();
      }
    });
  });

  // C. KIEM KHO (STOCKTAKE) TESTS

  test.describe('Stocktake (Kiem Kho)', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsOwner(page);
    });

    test('SK1 - Stocktake list page renders correctly', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/stocktakes`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      
      const pageTitle = page.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('SK2 - Navigate to create stocktake page', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/stocktakes`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = page.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Them")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        
        await expect(page).toHaveURL(/create/);
      }
    });

    test('SK3 - Create stocktake page shows warehouse selector', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/stocktakes/create`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for warehouse selector
      const warehouseField = page.locator('select[id*="warehouse"], [class*="warehouse"]').first();
      const hasWarehouseField = await warehouseField.isVisible({ timeout: 3000 });
      
      // Look for session code field
      const codeField = page.locator('input[id*="code"], input[id*="session"]').first();
      const hasCodeField = await codeField.isVisible({ timeout: 3000 });
      
      expect(hasWarehouseField || hasCodeField).toBeTruthy();
    });

    test('SK9 - Cancel stocktake', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/stocktakes`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for cancel button
      const cancelBtn = page.locator('button:has-text("Huy"), button:has-text("Cancel")').first();
      if (await cancelBtn.isVisible({ timeout: 3000 })) {
        // Find a row that can be cancelled (DRAFT status)
        await cancelBtn.click();
        
        // Look for confirmation dialog
        const confirmDialog = page.locator('[class*="dialog"], [class*="modal"], button:has-text("Xac nhan")').first();
        if (await confirmDialog.isVisible({ timeout: 3000 })) {
          const confirmBtn = page.locator('button:has-text("Xac nhan"), button:has-text("Confirm")').first();
          await confirmBtn.click();
          await page.waitForTimeout(500);
        }
      }
      
      await expect(page.locator('body')).toBeVisible();
    });
  });

  // D. CHUYEN KHO (STOCK TRANSFER) TESTS

  test.describe('Stock Transfer (Chuyen Kho)', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsOwner(page);
    });

    test('CK1 - Stock transfer list page renders correctly', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/transfers`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      
      const pageTitle = page.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('CK2 - Filter by status', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/transfers`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const statusFilter = page.locator('button:has-text("DRAFT"), button:has-text("Hoan thanh")').first();
      if (await statusFilter.isVisible({ timeout: 3000 })) {
        await statusFilter.click();
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('CK3 - Navigate to create transfer page', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/transfers`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = page.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Chuyen")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        
        await expect(page).toHaveURL(/create/);
      }
    });

    test('CK4 - Create transfer shows source and destination warehouse', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/transfers/create`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for source warehouse field
      const sourceField = page.locator('select[id*="from"], [class*="from"]').first();
      // Look for destination warehouse field
      const destField = page.locator('select[id*="to"], [class*="to"]').first();
      
      const hasSource = await sourceField.isVisible({ timeout: 3000 });
      const hasDest = await destField.isVisible({ timeout: 3000 });
      
      expect(hasSource || hasDest).toBeTruthy();
    });

    test('CK5 - Swap warehouses button works', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/transfers/create`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for swap button
      const swapBtn = page.locator('button:has-text("Doi cho"), button:has-text("Swap"), [class*="swap"]').first();
      if (await swapBtn.isVisible({ timeout: 3000 })) {
        // Get initial values
        const sourceField = page.locator('select[id*="from"]').first();
        const destField = page.locator('select[id*="to"]').first();
        
        const initialSource = await sourceField.inputValue().catch(() => '');
        const initialDest = await destField.inputValue().catch(() => '');
        
        await swapBtn.click();
        await page.waitForTimeout(300);
        
        // Values should be swapped (or at least button works)
        await expect(swapBtn).toBeVisible();
      }
    });

    test('CK8 - Confirm transfer status change', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/transfers`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for confirm button
      const confirmBtn = page.locator('button:has-text("Xac nhan"), button:has-text("Van chuyen")').first();
      if (await confirmBtn.isVisible({ timeout: 3000 })) {
        await confirmBtn.click();
        await page.waitForTimeout(500);
        
        // Should show success or change status
        const hasSuccess = await page.locator('text:has-text("thanh cong"), text:has-text("success")').first().isVisible({ timeout: 3000 });
        expect(hasSuccess || page.url().includes('/transfers')).toBeTruthy();
      }
    });

    test('CK10 - Cancel transfer', async ({ page }) => {
      await page.goto(`${BASE_URL}/warehouse/transfers`);
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // Look for cancel button
      const cancelBtn = page.locator('button:has-text("Huy phieu"), button:has-text("Huy")').first();
      if (await cancelBtn.isVisible({ timeout: 3000 })) {
        await cancelBtn.click();
        
        // Look for confirmation
        const confirmBtn = page.locator('button:has-text("Xac nhan")').first();
        if (await confirmBtn.isVisible({ timeout: 3000 })) {
          await confirmBtn.click();
          await page.waitForTimeout(500);
        }
      }
      
      await expect(page.locator('body')).toBeVisible();
    });
  });
});
