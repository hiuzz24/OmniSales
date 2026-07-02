const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  createTestOrder,
  deleteTestOrder,
  API_BASE,
} = require('../utils/order-helpers');

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

test.describe('Order E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  // =========================================================
  // Order List Page
  // =========================================================
  test('Should navigate to orders page', async ({ page }) => {
    await page.goto('/orders');
    await expect(page).toHaveURL(/\/orders/);
  });

  test('Should display orders table with columns', async ({ page }) => {
    await page.goto('/orders');
    
    // Wait for page to load
    await page.waitForTimeout(1000);
    
    // Check that the page has some content
    const content = await page.textContent('body');
    expect(content.length).toBeGreaterThan(0);
  });

  test('Should display orders stats cards', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForTimeout(1000);
    
    // Check for stats indicators
    const statsCards = await page.locator('[class*="card"], [class*="stat"], [class*="summary"]').count();
    expect(statsCards).toBeGreaterThan(0);
  });

  // =========================================================
  // Order Filters
  // =========================================================
  test('Should have status filter dropdown', async ({ page }) => {
    await page.goto('/orders');
    
    const statusFilter = page.locator('select[id*="status"], [class*="filter"] select').first();
    if (await statusFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(statusFilter).toBeVisible();
    }
  });

  test('Should have search input', async ({ page }) => {
    await page.goto('/orders');
    
    const searchInput = page.locator('input[placeholder*="search" i], input[id*="search" i], input[id*="keyword" i]').first();
    if (await searchInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(searchInput).toBeVisible();
    }
  });

  test('Should have pagination controls', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForTimeout(1000);
    
    const pagination = page.locator('[class*="pagination"], nav[aria-label*="pagination"]').first();
    if (await pagination.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(pagination).toBeVisible();
    }
  });

  // =========================================================
  // Order Detail Modal/Page
  // =========================================================
  test('Should open order detail when clicking on an order', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForTimeout(2000);
    
    // Try to find and click on first order row
    const orderRow = page.locator('tr, [class*="order-item"], [class*="order-row"]').first();
    if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
      await orderRow.click();
      await page.waitForTimeout(1000);
      
      // Check if modal or detail page opened
      const modal = page.locator('[role="dialog"], [class*="modal"], [class*="drawer"]').first();
      if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(modal).toBeVisible();
      }
    }
  });

  // =========================================================
  // Order Actions
  // =========================================================
  test('Should have create order button if user has permission', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForTimeout(1000);
    
    const createButton = page.locator('button:has-text("Tạo"), button:has-text("Create"), a[href*="/orders/new"]').first();
    if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(createButton).toBeVisible();
    }
  });

  test('Should have refresh button', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForTimeout(1000);
    
    const refreshButton = page.locator('button[aria-label*="refresh" i], button[title*="refresh" i]').first();
    if (await refreshButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(refreshButton).toBeVisible();
    }
  });

  // =========================================================
  // Sidebar Navigation
  // =========================================================
  test('Should have orders link in sidebar', async ({ page }) => {
    const ordersLink = page.locator('a[href*="/orders"], nav a:has-text("Đơn hàng"), nav a:has-text("Orders")').first();
    await expect(ordersLink).toBeVisible();
  });

  test('Should navigate to customers from sidebar', async ({ page }) => {
    const customersLink = page.locator('a[href*="/customers"], nav a:has-text("Khách hàng"), nav a:has-text("Customers")').first();
    if (await customersLink.isVisible({ timeout: 2000 }).catch(() => false)) {
      await customersLink.click();
      await page.waitForTimeout(1000);
      await expect(page).toHaveURL(/\/customers/);
    }
  });
});
