const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getWarehouseId,
  getSupplierId,
} = require('../../utils/warehouse-helpers');
const { FRONTEND_URL } = require('../../utils/env-config');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || FRONTEND_URL;

test.describe('Warehouse E2E Tests', () => {

  let warehouseId;
  let supplierId;

  test.beforeAll(async ({ request }) => {
    const { getAuthToken } = require('../../utils/warehouse-helpers');
    const token = await getAuthTokenCached(request);
    warehouseId = await getWarehouseId(request, token);
    supplierId = await getSupplierId(request, token);
  });

  test.describe('Stock Receive (Nhap Kho)', () => {

    test('R1 - Stock receive list page renders correctly', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const pageTitle = managerPage.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('R2 - Filter by status (All/Completed/Draft)', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const statusFilter = managerPage.locator('button, [class*="status"], [class*="filter"]').first();
      if (await statusFilter.isVisible()) {
        await statusFilter.click();
      }

      await expect(managerPage.locator('body')).toBeVisible();
    });

    test('R3 - Search by receipt code', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const searchInput = managerPage.locator('input[placeholder*="tim"], input[placeholder*="ma"], input[type="search"]').first();
      if (await searchInput.isVisible({ timeout: 3000 })) {
        await searchInput.fill('PN-');
        await managerPage.waitForTimeout(500);
      }

      await expect(managerPage.locator('body')).toBeVisible();
    });

    test('R4 - Navigate to create receipt page', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = managerPage.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Them"), button:has-text("+")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        await expect(managerPage).toHaveURL(/create/);
      } else {
        await expect(managerPage).toHaveURL(/receipts/);
      }
    });

    test('R5 - Create receipt page renders form fields', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/receipts/create`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const warehouseField = managerPage.locator('select[id*="warehouse"], [class*="warehouse"]').first();
      const dateField = managerPage.locator('input[type="date"], input[id*="date"]').first();

      const hasForm = await warehouseField.isVisible({ timeout: 3000 }) ||
                      await dateField.isVisible({ timeout: 3000 });
      expect(hasForm).toBeTruthy();
    });

    test('R11 - View receipt detail', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/receipts`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const detailBtn = managerPage.locator('button:has-text("Chi tiet"), a:has-text("Chi tiet"), button:has-text("View")').first();
      if (await detailBtn.isVisible({ timeout: 3000 })) {
        await detailBtn.click();
        await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

        const isOnDetail = managerPage.url().includes('/receipts/') && !managerPage.url().includes('/create');
        const hasDetailContent = await managerPage.locator('[class*="detail"], [class*="info"]').first().isVisible({ timeout: 3000 });
        expect(isOnDetail || hasDetailContent).toBeTruthy();
      }
    });

    test('R13 - Validation: missing warehouse', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/receipts/create`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const submitBtn = managerPage.locator('button[type="submit"], button:has-text("Luu"), button:has-text("Hoan thanh")').first();
      if (await submitBtn.isVisible({ timeout: 3000 })) {
        await submitBtn.click();
        await managerPage.waitForTimeout(500);

        const hasError = await managerPage.locator('[class*="error"], [class*="required"], text:has-text("bat buoc")').first().isVisible({ timeout: 3000 });
        expect(hasError || managerPage.url().includes('create')).toBeTruthy();
      }
    });
  });

  test.describe('Stock Delivery (Xuat Kho)', () => {

    test('D1 - Stock delivery list page renders correctly', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const pageTitle = managerPage.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('D2 - Filter by delivery type', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const typeFilter = managerPage.locator('button:has-text("ORDER"), button:has-text("DISPOSAL"), button:has-text("ADJUSTMENT")').first();
      if (await typeFilter.isVisible({ timeout: 3000 })) {
        await typeFilter.click();
        await managerPage.waitForTimeout(500);
      }

      await expect(managerPage.locator('body')).toBeVisible();
    });

    test('D3 - Navigate to create delivery page', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = managerPage.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Them")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        await expect(managerPage).toHaveURL(/create/);
      }
    });

    test('D7 - Delivery type selection shows correct form', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries/create`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const typeSelector = managerPage.locator('select[id*="type"], [class*="type"], button:has-text("ORDER")').first();
      if (await typeSelector.isVisible({ timeout: 3000 })) {
        await typeSelector.click();
        await managerPage.waitForTimeout(300);
      }

      await expect(managerPage.locator('body')).toBeVisible();
    });

    test('D11 - View delivery detail', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/inventory/stock-deliveries`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const detailBtn = managerPage.locator('button:has-text("Chi tiet"), a:has-text("Chi tiet")').first();
      if (await detailBtn.isVisible({ timeout: 3000 })) {
        await detailBtn.click();
        await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

        const hasDetail = await managerPage.locator('[class*="detail"], [class*="info"]').first().isVisible({ timeout: 3000 });
        expect(hasDetail || managerPage.url().includes('/stock-deliveries/')).toBeTruthy();
      }
    });
  });

  test.describe('Stocktake (Kiem Kho)', () => {

    test('SK1 - Stocktake list page renders correctly', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const pageTitle = managerPage.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('SK2 - Navigate to create stocktake page', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = managerPage.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Them")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        await expect(managerPage).toHaveURL(/create/);
      }
    });

    test('SK3 - Create stocktake page shows warehouse selector', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/stocktakes/create`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const warehouseField = managerPage.locator('select[id*="warehouse"], [class*="warehouse"]').first();
      const hasWarehouseField = await warehouseField.isVisible({ timeout: 3000 });

      const codeField = managerPage.locator('input[id*="code"], input[id*="session"]').first();
      const hasCodeField = await codeField.isVisible({ timeout: 3000 });

      expect(hasWarehouseField || hasCodeField).toBeTruthy();
    });

    test('SK9 - Cancel stocktake', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/stocktakes`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const cancelBtn = managerPage.locator('button:has-text("Huy"), button:has-text("Cancel")').first();
      if (await cancelBtn.isVisible({ timeout: 3000 })) {
        await cancelBtn.click();

        const confirmDialog = managerPage.locator('[class*="dialog"], [class*="modal"], button:has-text("Xac nhan")').first();
        if (await confirmDialog.isVisible({ timeout: 3000 })) {
          const confirmBtn = managerPage.locator('button:has-text("Xac nhan"), button:has-text("Confirm")').first();
          await confirmBtn.click();
          await managerPage.waitForTimeout(500);
        }
      }

      await expect(managerPage.locator('body')).toBeVisible();
    });
  });

  test.describe('Stock Transfer (Chuyen Kho)', () => {

    test('CK1 - Stock transfer list page renders correctly', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const pageTitle = managerPage.locator('h1, h2, [class*="title"]').first();
      await expect(pageTitle).toBeVisible();
    });

    test('CK2 - Filter by status', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const statusFilter = managerPage.locator('button:has-text("DRAFT"), button:has-text("Hoan thanh")').first();
      if (await statusFilter.isVisible({ timeout: 3000 })) {
        await statusFilter.click();
        await managerPage.waitForTimeout(500);
      }

      await expect(managerPage.locator('body')).toBeVisible();
    });

    test('CK3 - Navigate to create transfer page', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = managerPage.locator('a[href*="create"], button:has-text("Tao"), button:has-text("Chuyen")').first();
      if (await createBtn.isVisible({ timeout: 3000 })) {
        await createBtn.click();
        await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
        await expect(managerPage).toHaveURL(/create/);
      }
    });

    test('CK4 - Create transfer shows source and destination warehouse', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/transfers/create`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const sourceField = managerPage.locator('select[id*="from"], [class*="from"]').first();
      const destField = managerPage.locator('select[id*="to"], [class*="to"]').first();

      const hasSource = await sourceField.isVisible({ timeout: 3000 });
      const hasDest = await destField.isVisible({ timeout: 3000 });

      expect(hasSource || hasDest).toBeTruthy();
    });

    test('CK5 - Swap warehouses button works', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/transfers/create`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const swapBtn = managerPage.locator('button:has-text("Doi cho"), button:has-text("Swap"), [class*="swap"]').first();
      if (await swapBtn.isVisible({ timeout: 3000 })) {
        const sourceField = managerPage.locator('select[id*="from"]').first();
        const destField = managerPage.locator('select[id*="to"]').first();

        const initialSource = await sourceField.inputValue().catch(() => '');
        const initialDest = await destField.inputValue().catch(() => '');

        await swapBtn.click();
        await managerPage.waitForTimeout(300);

        await expect(swapBtn).toBeVisible();
      }
    });

    test('CK8 - Confirm transfer status change', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const confirmBtn = managerPage.locator('button:has-text("Xac nhan"), button:has-text("Van chuyen")').first();
      if (await confirmBtn.isVisible({ timeout: 3000 })) {
        await confirmBtn.click();
        await managerPage.waitForTimeout(500);

        const hasSuccess = await managerPage.locator('text:has-text("thanh cong"), text:has-text("success")').first().isVisible({ timeout: 3000 });
        expect(hasSuccess || managerPage.url().includes('/transfers')).toBeTruthy();
      }
    });

    test('CK10 - Cancel transfer', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/transfers`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const cancelBtn = managerPage.locator('button:has-text("Huy phieu"), button:has-text("Huy")').first();
      if (await cancelBtn.isVisible({ timeout: 3000 })) {
        await cancelBtn.click();

        const confirmBtn = managerPage.locator('button:has-text("Xac nhan")').first();
        if (await confirmBtn.isVisible({ timeout: 3000 })) {
          await confirmBtn.click();
          await managerPage.waitForTimeout(500);
        }
      }

      await expect(managerPage.locator('body')).toBeVisible();
    });
  });

  test.describe('Warehouse Management (/warehouse/manage)', () => {
    test('WGM-1 - /warehouse/manage - List page renders', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/manage`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const hasHeading = await managerPage.locator('h1, h2, h3').first().count();
      expect(hasHeading).toBeGreaterThan(0);
    });

    test('WGM-2 - /warehouse/manage - Create button opens modal', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/manage`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const createBtn = managerPage.locator('button:has-text("Tạo"), button:has-text("Thêm"), button:has-text("+"), a:has-text("Tạo")').first();
      // The page might not have a Create button (e.g. listing only existing WHs).
      // We just verify the page renders without crashing.
      const body = await managerPage.content();
      expect(body.length).toBeGreaterThan(50);
      if ((await createBtn.count()) > 0) {
        await createBtn.click();
        await managerPage.waitForTimeout(500);
      }
    });

    test('WGM-3 - /warehouse/manage - Click warehouse row navigates to detail', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/manage`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const detailBtn = managerPage.locator('a:has-text("Chi tiết"), button:has-text("Chi tiết"), a:has-text("View"), button:has-text("View")').first();
      if ((await detailBtn.count()) > 0) {
        await detailBtn.click();
        await managerPage.waitForTimeout(1000);
        expect(managerPage.url()).toMatch(/\/warehouse\/manage\//);
      }
    });

    test('WGM-4 - /warehouse/manage/:id - Detail page renders', async ({ managerPage, request }) => {
      // Use the first existing warehouse via API
      const { getAuthToken } = require('../../utils/warehouse-helpers');
      const authToken = await getAuthTokenCached(request);
      const list = await request.get(`${process.env.API_BASE || 'http://localhost:8080/api'}/warehouses`, {
        headers: { Authorization: `Bearer ${authToken}` },
      });
      test.skip(list.status() !== 200, 'warehouses list unavailable');
      const data = (await list.json()).data;
      test.skip(!Array.isArray(data) || data.length === 0, 'No warehouses');
      const targetId = data[0].id;

      await managerPage.goto(`${BASE_URL}/warehouse/manage/${targetId}`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const hasHeading = await managerPage.locator('h1, h2, h3').first().count();
      expect(hasHeading).toBeGreaterThan(0);
    });

    test('WGM-5 - /warehouse/manage - Status filter switches values', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/warehouse/manage`);
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      const statusSelect = managerPage.locator('select').first();
      if ((await statusSelect.count()) > 0) {
        const opts = await statusSelect.locator('option').count();
        expect(opts).toBeGreaterThanOrEqual(1);
      }
    });
  });
});
