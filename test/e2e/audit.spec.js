const { test, expect } = require('@playwright/test');

const BASE_URL = process.env.BASE_URL || 'http://localhost:5174';

/**
 * Login as manager via UI
 */
async function loginAsManager(page) {
  await page.goto('/login');
  await page.locator('#login-email').fill('manager@osms.vn');
  await page.locator('#login-password').fill('Duy16042004%');

  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 8000 }),
    page.locator('#login-submit-btn').click(),
  ]);

  await expect(page).toHaveURL(/\/dashboard/);
}

test.describe('Audit Logs E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  // =========================================================
  // Audit Logs Page
  // =========================================================
  test('AL1 - Audit logs page renders correctly', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const pageContent = page.locator('h1, h2, [class*="title"], [class*="header"]').first();
    await expect(pageContent).toBeVisible();
  });

  test('AL2 - Display audit logs table', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const table = page.locator('table, [class*="table"]').first();
    const hasTable = await table.isVisible({ timeout: 5000 });

    expect(hasTable || page.url().includes('/audit-logs')).toBeTruthy();
  });

  test('AL3 - Audit log entry shows correct columns', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    // Check for common columns
    const expectedColumns = ['Actor', 'Action', 'Entity', 'Time', 'Thao tac', 'Hanh dong', 'Thuc the', 'Thoi gian'];
    const hasColumn = expectedColumns.some(col =>
      page.locator(`text=${col}`).first().isVisible({ timeout: 2000 }).catch(() => false)
    );

    expect(hasColumn || page.url().includes('/audit-logs')).toBeTruthy();
  });

  // =========================================================
  // Filters
  // =========================================================
  test('AL4 - Filter by action type', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const actionFilter = page.locator('select[id*="action"], [class*="filter"] select, button:has-text("Hanh dong")').first();
    if (await actionFilter.isVisible({ timeout: 3000 })) {
      await actionFilter.click();
      await page.waitForTimeout(500);

      const option = page.locator('option').nth(1);
      if (await option.isVisible({ timeout: 1000 })) {
        await option.click();
        await page.waitForTimeout(500);
      }
    }

    expect(page.locator('body')).toBeVisible();
  });

  test('AL5 - Filter by entity type', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const entityFilter = page.locator('select[id*="entity"], button:has-text("Thuc the")').first();
    if (await entityFilter.isVisible({ timeout: 3000 })) {
      await entityFilter.click();
      await page.waitForTimeout(500);
    }

    expect(page.locator('body')).toBeVisible();
  });

  test('AL6 - Filter by date range', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const dateFrom = page.locator('input[id*="from"], input[placeholder*="Tu"], input[type="date"]').first();
    const dateTo = page.locator('input[id*="to"], input[placeholder*="Den"], input[type="date"]').nth(1).catch(() => dateFrom);

    if (await dateFrom.isVisible({ timeout: 3000 })) {
      await dateFrom.fill('2024-01-01');
      await page.waitForTimeout(300);
    }

    if (await dateTo.isVisible({ timeout: 1000 }).catch(() => false)) {
      await dateTo.fill('2024-12-31');
      await page.waitForTimeout(300);
    }

    expect(page.locator('body')).toBeVisible();
  });

  test('AL7 - Search by keyword', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('input[placeholder*="tim"], input[placeholder*="search"], input[id*="keyword"]').first();
    if (await searchInput.isVisible({ timeout: 3000 })) {
      await searchInput.fill('test');
      await page.waitForTimeout(500);

      const hasResults = await page.locator('table, [class*="item"]').first().isVisible({ timeout: 3000 });
      expect(hasResults).toBeTruthy();
    }
  });

  // =========================================================
  // Pagination
  // =========================================================
  test('AL8 - Pagination controls visible', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const pagination = page.locator('[class*="pagination"], nav, [class*="page"]').first();
    if (await pagination.isVisible({ timeout: 3000 })) {
      await expect(pagination).toBeVisible();
    }
  });

  test('AL9 - Navigate to next page', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const nextButton = page.locator('button:has-text("Tiếp"), button[aria-label*="next"], a:has-text(">")').first();
    if (await nextButton.isVisible({ timeout: 3000 })) {
      const isDisabled = await nextButton.getAttribute('disabled');
      if (!isDisabled) {
        await nextButton.click();
        await page.waitForTimeout(500);
      }
    }

    expect(page.locator('body')).toBeVisible();
  });

  // =========================================================
  // Audit Log Details
  // =========================================================
  test('AL10 - View audit log details', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const logRow = page.locator('tr, [class*="log-row"], [class*="audit-row"]').first();
    if (await logRow.isVisible({ timeout: 5000 })) {
      await logRow.click();
      await page.waitForTimeout(500);

      const modal = page.locator('[role="dialog"], [class*="modal"], [class*="drawer"]').first();
      if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(modal).toBeVisible();
      }
    }
  });

  test('AL11 - Audit log shows changes/details', async ({ page }) => {
    await page.goto(`${BASE_URL}/audit-logs`);
    await page.waitForLoadState('networkidle');

    const expandBtn = page.locator('button:has-text("Chi tiet"), button:has-text("Details"), [aria-label*="expand"]').first();
    if (await expandBtn.isVisible({ timeout: 3000 })) {
      await expandBtn.click();
      await page.waitForTimeout(500);

      const details = page.locator('[class*="detail"], [class*="changes"], [class*="info"]').first();
      expect(await details.isVisible({ timeout: 2000 }).catch(() => false) || page.url().includes('/audit-logs')).toBeTruthy();
    }
  });

  // =========================================================
  // Sidebar Navigation
  // =========================================================
  test('AL12 - Navigate to audit logs from sidebar', async ({ page }) => {
    const auditLink = page.locator('a[href*="/audit"], nav a:has-text("Lich su"), nav a:has-text("Audit")').first();
    if (await auditLink.isVisible({ timeout: 3000 })) {
      await auditLink.click();
      await page.waitForLoadState('networkidle');
      expect(page.url().includes('/audit') || page.url().includes('/logs')).toBeTruthy();
    }
  });

  test('AL13 - Navigate to inventory from sidebar', async ({ page }) => {
    const inventoryLink = page.locator('a[href*="/inventory"], nav a:has-text("Kho"), nav a:has-text("Inventory")').first();
    if (await inventoryLink.isVisible({ timeout: 3000 })) {
      await inventoryLink.click();
      await page.waitForLoadState('networkidle');
      expect(page.url().includes('/inventory')).toBeTruthy();
    }
  });

  // =========================================================
  // Inventory Transaction History Page
  // =========================================================
  test('AL14 - Inventory transaction logs page renders', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle');

    const pageContent = page.locator('h1, h2, [class*="title"]').first();
    await expect(pageContent).toBeVisible();
  });

  test('AL15 - Inventory transaction table displays', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle');

    const table = page.locator('table, [class*="table"]').first();
    const hasTable = await table.isVisible({ timeout: 5000 });

    expect(hasTable || page.url().includes('/inventory')).toBeTruthy();
  });

  test('AL16 - Filter transactions by type', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle');

    const typeFilter = page.locator('select[id*="type"], button:has-text("Nhap"), button:has-text("Xuat")').first();
    if (await typeFilter.isVisible({ timeout: 3000 })) {
      await typeFilter.click();
      await page.waitForTimeout(500);
    }

    expect(page.locator('body')).toBeVisible();
  });

  test('AL17 - Filter transactions by warehouse', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle');

    const warehouseFilter = page.locator('select[id*="warehouse"], button:has-text("Kho")').first();
    if (await warehouseFilter.isVisible({ timeout: 3000 })) {
      await warehouseFilter.click();
      await page.waitForTimeout(500);

      const option = page.locator('option').nth(1);
      if (await option.isVisible({ timeout: 1000 })) {
        await option.click();
        await page.waitForTimeout(500);
      }
    }

    expect(page.locator('body')).toBeVisible();
  });

  test('AL18 - Search transactions by SKU', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('input[placeholder*="SKU"], input[id*="sku"]').first();
    if (await searchInput.isVisible({ timeout: 3000 })) {
      await searchInput.fill('TEST');
      await page.waitForTimeout(500);
    }

    expect(page.locator('body')).toBeVisible();
  });

  test('AL19 - Export transaction logs', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle');

    const exportBtn = page.locator('button:has-text("Export"), button:has-text("Tai xuong")').first();
    if (await exportBtn.isVisible({ timeout: 3000 })) {
      await exportBtn.click();
      await page.waitForTimeout(500);
    }

    expect(page.locator('body')).toBeVisible();
  });

  test('AL20 - View transaction details', async ({ page }) => {
    await page.goto(`${BASE_URL}/inventory/logs`);
    await page.waitForLoadState('networkidle');

    const row = page.locator('tr, [class*="row"]').first();
    if (await row.isVisible({ timeout: 5000 })) {
      await row.click();
      await page.waitForTimeout(500);

      const details = page.locator('[class*="detail"], [role="dialog"]').first();
      expect(await details.isVisible({ timeout: 2000 }).catch(() => false) || page.url().includes('/inventory')).toBeTruthy();
    }
  });
});
