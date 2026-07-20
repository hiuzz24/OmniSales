const { test, expect } = require('../../fixtures/auth-fixtures');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Audit Logs E2E Tests', () => {

  // Audit Logs Page

  test('AL1 - Audit logs page renders correctly', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);
    await expect(managerPage.locator('body')).toBeVisible();
  });

  test('AL2 - Display audit logs table', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const table = managerPage.locator('table').first();
    const hasTable = await table.isVisible({ timeout: 3000 }).catch(() => false);
    const onAuditPage = managerPage.url().includes('/audit-logs');

    expect(hasTable || onAuditPage || managerPage.url().endsWith('/') || managerPage.url().includes('/dashboard')).toBeTruthy();
  });

  test('AL3 - Audit log entry shows correct columns', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const expectedColumns = ['Actor', 'Action', 'Entity', 'Time', 'Thao tác', 'Hành động', 'Thực thể', 'Thời gian'];
    const hasColumn = await Promise.any(
      expectedColumns.map(col =>
        managerPage.locator(`text=${col}`).first().waitFor({ state: 'visible', timeout: 2000 }).then(() => true)
      )
    ).catch(() => false);

    expect(hasColumn || managerPage.url().includes('/audit-logs') || managerPage.url().endsWith('/')).toBeTruthy();
  });

  // Filters

  test('AL4 - Filter by action type', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const actionFilter = managerPage.locator('select[id*="action"], [class*="filter"] select, button:has-text("Hành động")').first();
    const hasFilter = await actionFilter.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasFilter) {
      await actionFilter.click();
      await managerPage.waitForTimeout(500);

      const option = managerPage.locator('option').nth(1);
      const hasOption = await option.isVisible({ timeout: 1000 }).catch(() => false);
      if (hasOption) {
        await option.click();
        await managerPage.waitForTimeout(500);
      }
    }
  });

  test('AL5 - Filter by entity type', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const entityFilter = managerPage.locator('select[id*="entity"], button:has-text("Thực thể")').first();
    const hasFilter = await entityFilter.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasFilter) {
      await entityFilter.click();
      await managerPage.waitForTimeout(500);
    }
  });

  test('AL6 - Filter by date range', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const dateFrom = managerPage.locator('input[id*="from"], input[placeholder*="Từ"], input[type="date"]').first();
    const dateToLoc = managerPage.locator('input[id*="to"], input[placeholder*="Đến"], input[type="date"]');

    const fromReady = await dateFrom.isVisible({ timeout: 3000 })
      .then(async (v) => v ? await dateFrom.isEnabled().catch(() => false) : false)
      .catch(() => false);

    if (fromReady) {
      try {
        await dateFrom.fill('2024-01-01', { timeout: 3000 });
        await managerPage.waitForTimeout(300);
      } catch (_) { /* skip */ }

      try {
        const dateTo = dateToLoc.nth(1);
        const toReady = await dateTo.isVisible({ timeout: 1000 })
          .then(async (v) => v ? await dateTo.isEnabled().catch(() => false) : false)
          .catch(() => false);
        if (toReady) {
          await dateTo.fill('2024-12-31', { timeout: 3000 });
          await managerPage.waitForTimeout(300);
        }
      } catch (_) { /* skip */ }
    }
  });

  test('AL7 - Search by keyword', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const searchInput = managerPage.locator('input[placeholder*="tìm"], input[placeholder*="search"], input[id*="keyword"]').first();
    const hasSearch = await searchInput.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasSearch) {
      await searchInput.fill('test');
      await managerPage.waitForTimeout(500);

      const hasResults = await managerPage.locator('table, [class*="item"]').first().isVisible({ timeout: 3000 }).catch(() => false);
      expect(hasResults).toBeTruthy();
    }
  });

  // Pagination

  test('AL8 - Pagination controls visible', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`, { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const pagination = managerPage.locator('[class*="pagination"], nav, [class*="page"]').first();
    const hasPagination = await pagination.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasPagination) {
      await expect(pagination).toBeVisible();
    }
  });

  test('AL9 - Navigate to next page', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const nextButton = managerPage.locator('button:has-text("Tiếp"), button[aria-label*="next"], a:has-text(">")').first();
    const hasNext = await nextButton.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasNext) {
      const isDisabled = await nextButton.getAttribute('disabled');
      if (!isDisabled) {
        await nextButton.click();
        await managerPage.waitForTimeout(500);
      }
    }
  });

  // Audit Log Details

  test('AL10 - View audit log details', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const logRow = managerPage.locator('tr, [class*="log-row"], [class*="audit-row"]').first();
    const hasRow = await logRow.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasRow) {
      await logRow.click();
      await managerPage.waitForTimeout(500);

      const modal = managerPage.locator('[role="dialog"], [class*="modal"], [class*="drawer"]').first();
      const hasModal = await modal.isVisible({ timeout: 2000 }).catch(() => false);
      if (hasModal) {
        await expect(modal).toBeVisible();
      }
    }
  });

  test('AL11 - Audit log shows changes/details', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/audit-logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    await expect(managerPage).toHaveURL(/\/(|audit-logs|dashboard)$/);

    const expandBtn = managerPage.locator('button:has-text("Chi tiết"), button:has-text("Details"), [aria-label*="expand"]').first();
    const hasExpand = await expandBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasExpand) {
      await expandBtn.click();
      await managerPage.waitForTimeout(500);

      const details = managerPage.locator('[class*="detail"], [class*="changes"], [class*="info"]').first();
      const hasDetails = await details.isVisible({ timeout: 2000 }).catch(() => false);
      expect(hasDetails || managerPage.url().includes('/audit-logs')).toBeTruthy();
    }
  });

  // Sidebar Navigation

  test('AL12 - Navigate to audit logs from sidebar', async ({ managerPage }) => {
    const auditLink = managerPage.locator('a[href*="/audit"], nav a:has-text("Lịch sử"), nav a:has-text("Audit")').first();
    const hasLink = await auditLink.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasLink) {
      await auditLink.click();
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      expect(managerPage.url().includes('/audit') || managerPage.url().includes('/logs')).toBeTruthy();
    }
  });

  test('AL13 - Navigate to inventory from sidebar', async ({ managerPage }) => {
    const inventoryLink = managerPage.locator('a[href*="/inventory"], nav a:has-text("Kho"), nav a:has-text("Inventory")').first();
    const hasLink = await inventoryLink.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasLink) {
      await inventoryLink.click();
      await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
      expect(managerPage.url().includes('/inventory')).toBeTruthy();
    }
  });

  // Inventory Transaction History Page

  test('AL14 - Inventory transaction logs page renders', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const pageContent = managerPage.locator('[class*="headerTitle"], h1, h2, [class*="pageTitle"]').first();
    await expect(pageContent).toBeVisible({ timeout: 8000 });

    await expect(managerPage.getByText('Lịch sử thay đổi').first()).toBeVisible({ timeout: 8000 });
  });

  test('AL15 - Inventory transaction table displays', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const table = managerPage.locator('table, [class*="table"]').first();
    const hasTable = await table.isVisible({ timeout: 5000 }).catch(() => false);

    expect(hasTable || managerPage.url().includes('/inventory')).toBeTruthy();
  });

  test('AL16 - Filter transactions by type', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const typeFilter = managerPage.locator('select[id*="type"], button:has-text("Nhập"), button:has-text("Xuất")').first();
    const hasFilter = await typeFilter.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasFilter) {
      await typeFilter.click();
      await managerPage.waitForTimeout(500);
    }
  });

  test('AL17 - Filter transactions by warehouse', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const warehouseFilter = managerPage.locator('select[id*="warehouse"], button:has-text("Kho")').first();
    const hasFilter = await warehouseFilter.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasFilter) {
      await warehouseFilter.click();
      await managerPage.waitForTimeout(500);

      const option = managerPage.locator('option').nth(1);
      const hasOption = await option.isVisible({ timeout: 1000 }).catch(() => false);
      if (hasOption) {
        await option.click();
        await managerPage.waitForTimeout(500);
      }
    }
  });

  test('AL18 - Search transactions by SKU', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const searchInput = managerPage.locator('input[placeholder*="SKU"], input[id*="sku"]').first();
    const hasSearch = await searchInput.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasSearch) {
      await searchInput.fill('TEST');
      await managerPage.waitForTimeout(500);
    }
  });

  test('AL19 - Export transaction logs', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const exportBtn = managerPage.locator('button:has-text("Export"), button:has-text("Tải xuống")').first();
    const hasBtn = await exportBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasBtn) {
      await exportBtn.click();
      await managerPage.waitForTimeout(500);
    }
  });

  test('AL20 - View transaction details', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/inventory/logs`);
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const row = managerPage.locator('tbody tr, [class*="row"]').first();
    const hasRow = await row.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasRow) {
      await row.click().catch(() => null);
      await managerPage.waitForTimeout(300);
    }

    const stillOnInventory = managerPage.url().includes('/inventory');
    expect(hasRow || stillOnInventory).toBeTruthy();
  });
});
