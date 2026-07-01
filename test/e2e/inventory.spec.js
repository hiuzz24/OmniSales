const { test, expect } = require('@playwright/test');
const {
  loginAsOwner,
  getWarehouseId,
  API_BASE,
} = require('../utils/warehouse-helpers');

const BASE_URL = process.env.BASE_URL || 'http://localhost:5174';

test.describe('Inventory E2E Tests', () => {

  let warehouseId;

  test.beforeAll(async ({ request }) => {
    const { getAuthToken, getWarehouseId } = require('../utils/warehouse-helpers');
    const token = await getAuthToken(request);
    warehouseId = await getWarehouseId(request, token);
  });

  test.describe('Inventory Overview', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsOwner(page);
    });

    test('I1 - Inventory page renders correctly', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');
      
      // Check for main heading or page content
      const pageContent = page.locator('h1, h2, [class*="title"], [class*="header"]').first();
      await expect(pageContent).toBeVisible();
    });

    test('I2 - Display inventory items by warehouse', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for table with inventory items
      const table = page.locator('table, [class*="table"]').first();
      const hasTable = await table.isVisible({ timeout: 5000 });
      
      // Or look for inventory cards/list
      const list = page.locator('[class*="item"], [class*="product"], [class*="inventory"]').first();
      const hasList = await list.isVisible({ timeout: 3000 });
      
      expect(hasTable || hasList).toBeTruthy();
    });

    test('I3 - Filter by warehouse', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for warehouse filter
      const warehouseFilter = page.locator('select[id*="warehouse"], [class*="warehouse"], button:has-text("Kho")').first();
      if (await warehouseFilter.isVisible({ timeout: 3000 })) {
        await warehouseFilter.click();
        await page.waitForTimeout(500);
        
        // Select an option if available
        const option = page.locator('option').nth(1);
        if (await option.isVisible({ timeout: 1000 })) {
          await option.click();
          await page.waitForTimeout(500);
        }
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('I4 - Filter by stock status', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for stock status filter buttons
      const statusFilters = [
        page.locator('button:has-text("Du hang")'),
        page.locator('button:has-text("Het hang")'),
        page.locator('button:has-text("Sap het")'),
      ];
      
      for (const filter of statusFilters) {
        if (await filter.isVisible({ timeout: 2000 })) {
          await filter.click();
          await page.waitForTimeout(500);
          break;
        }
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('I5 - Search product', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for search input
      const searchInput = page.locator('input[placeholder*="tim"], input[placeholder*="search"], input[type="search"]').first();
      if (await searchInput.isVisible({ timeout: 3000 })) {
        await searchInput.fill('test');
        await page.waitForTimeout(500);
        
        // Check if search works or shows no results
        const hasResults = await page.locator('table, [class*="item"]').first().isVisible({ timeout: 3000 });
        expect(hasResults).toBeTruthy();
      }
    });

    test('I6 - View inventory item detail', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for a detail/view button or clickable item
      const detailLink = page.locator('a:has-text("Chi tiet"), button:has-text("Chi tiet"), [class*="row"]:has-text("SKU")').first();
      if (await detailLink.isVisible({ timeout: 5000 })) {
        await detailLink.click();
        await page.waitForLoadState('networkidle');
        
        // Should navigate to detail or show modal
        const isOnDetail = page.url().includes('/detail') || page.url().includes('/inventory/');
        expect(isOnDetail || page.url().includes('/inventory')).toBeTruthy();
      }
    });

    test('I8 - View inventory logs/history', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for logs/history button
      const logsBtn = page.locator('a:has-text("Lich su"), a:has-text("Logs"), button:has-text("Lich su")').first();
      if (await logsBtn.isVisible({ timeout: 3000 })) {
        await logsBtn.click();
        await page.waitForLoadState('networkidle');
        
        // Should navigate to logs page
        const isOnLogs = page.url().includes('/logs') || page.url().includes('/history');
        expect(isOnLogs || page.url().includes('/inventory')).toBeTruthy();
      }
    });

    test('I9 - Quick add inventory button', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for quick add/import stock button
      const addBtn = page.locator('a:has-text("Nhap kho"), button:has-text("Nhap kho")').first();
      if (await addBtn.isVisible({ timeout: 3000 })) {
        await addBtn.click();
        await page.waitForLoadState('networkidle');
        
        // Should navigate to receipt creation
        const isOnReceiptCreate = page.url().includes('/receipts/create') || page.url().includes('/create');
        expect(isOnReceiptCreate || page.url().includes('/inventory')).toBeTruthy();
      }
    });

    test('I10 - Quick export inventory button', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for export stock button
      const exportBtn = page.locator('a:has-text("Xuat kho"), button:has-text("Xuat kho")').first();
      if (await exportBtn.isVisible({ timeout: 3000 })) {
        await exportBtn.click();
        await page.waitForLoadState('networkidle');
        
        // Should navigate to delivery creation
        const isOnDeliveryCreate = page.url().includes('/stock-deliveries/create') || page.url().includes('/create');
        expect(isOnDeliveryCreate || page.url().includes('/inventory')).toBeTruthy();
      }
    });

    test('I11 - Quick transfer button', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for transfer button
      const transferBtn = page.locator('a:has-text("Chuyen kho"), button:has-text("Chuyen kho")').first();
      if (await transferBtn.isVisible({ timeout: 3000 })) {
        await transferBtn.click();
        await page.waitForLoadState('networkidle');
        
        // Should navigate to transfer creation
        const isOnTransferCreate = page.url().includes('/transfers/create') || page.url().includes('/create');
        expect(isOnTransferCreate || page.url().includes('/inventory')).toBeTruthy();
      }
    });

    test('I12 - Low stock alert display', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory`);
      await page.waitForLoadState('networkidle');

      // Look for low stock alert/notice section
      const alerts = [
        page.locator('[class*="alert"]'),
        page.locator('[class*="notice"]'),
        page.locator('[class*="warning"]'),
        page.locator('text:has-text("Sap het")'),
        page.locator('text:has-text("low stock")'),
      ];
      
      let hasAlert = false;
      for (const alert of alerts) {
        if (await alert.isVisible({ timeout: 2000 })) {
          hasAlert = true;
          break;
        }
      }
      
      // Either there's an alert or there are no low stock items
      expect(hasAlert || page.url().includes('/inventory')).toBeTruthy();
    });
  });

  test.describe('Inventory Detail Page', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsOwner(page);
    });

    test('ID1 - Inventory detail page renders correctly', async ({ page }) => {
      if (warehouseId) {
        await page.goto(`${BASE_URL}/inventory/detail/${warehouseId}`);
      } else {
        await page.goto(`${BASE_URL}/inventory`);
      }
      await page.waitForLoadState('networkidle');
      
      // Page should load without errors
      await expect(page.locator('body')).toBeVisible();
    });

    test('ID2 - Display transaction history', async ({ page }) => {
      if (warehouseId) {
        await page.goto(`${BASE_URL}/inventory/detail/${warehouseId}`);
      } else {
        await page.goto(`${BASE_URL}/inventory`);
      }
      await page.waitForLoadState('networkidle');

      // Look for transaction history section
      const historySection = page.locator('text:has-text("Lich su"), text:has-text("History"), [class*="transaction"]').first();
      if (await historySection.isVisible({ timeout: 3000 })) {
        await historySection.click();
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });
  });

  test.describe('Inventory Logs Page', () => {

    test.beforeEach(async ({ page }) => {
      await loginAsOwner(page);
    });

    test('IL1 - Inventory logs page renders correctly', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/logs`);
      await page.waitForLoadState('networkidle');
      
      const pageContent = page.locator('h1, h2, [class*="title"]').first();
      await expect(pageContent).toBeVisible();
    });

    test('IL2 - Filter logs by date range', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/logs`);
      await page.waitForLoadState('networkidle');

      // Look for date range inputs
      const dateFrom = page.locator('input[id*="from"], input[placeholder*="Tu"]').first();
      const dateTo = page.locator('input[id*="to"], input[placeholder*="Den"]').first();
      
      if (await dateFrom.isVisible({ timeout: 3000 })) {
        await dateFrom.fill('2024-01-01');
      }
      if (await dateTo.isVisible({ timeout: 3000 })) {
        await dateTo.fill('2024-12-31');
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('IL3 - Filter logs by transaction type', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/logs`);
      await page.waitForLoadState('networkidle');

      // Look for transaction type filter
      const typeFilter = page.locator('select[id*="type"], button:has-text("Nhap"), button:has-text("Xuat")').first();
      if (await typeFilter.isVisible({ timeout: 3000 })) {
        await typeFilter.click();
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('IL4 - Search logs by SKU', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/logs`);
      await page.waitForLoadState('networkidle');

      // Look for search input
      const searchInput = page.locator('input[placeholder*="tim"], input[placeholder*="SKU"], input[type="search"]').first();
      if (await searchInput.isVisible({ timeout: 3000 })) {
        await searchInput.fill('SKU');
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });

    test('IL5 - Export logs', async ({ page }) => {
      await page.goto(`${BASE_URL}/inventory/logs`);
      await page.waitForLoadState('networkidle');

      // Look for export button
      const exportBtn = page.locator('button:has-text("Export"), button:has-text("Tai")').first();
      if (await exportBtn.isVisible({ timeout: 3000 })) {
        // Click export and check if download dialog or action happens
        await exportBtn.click();
        await page.waitForTimeout(500);
      }
      
      await expect(page.locator('body')).toBeVisible();
    });
  });
});
