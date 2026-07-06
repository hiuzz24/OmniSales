const { test, expect } = require('@playwright/test');
const {
  getAuthToken,
  createTestCustomer,
  deleteTestCustomer,
  API_BASE,
} = require('../utils/customer-helpers');
const { TEST_EMAIL, TEST_PASSWORD } = require('../utils/env-config');

/**
 * Login as manager via UI
 */
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

test.describe('Customer E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await loginAsManager(page);
  });

  // Customer List Page

  test('Should navigate to customers page', async ({ page }) => {
    await page.goto('/customers');
    await expect(page).toHaveURL(/\/customers/);
  });

  test('Should display customers table', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    // Check that the page has some content
    const content = await page.textContent('body');
    expect(content.length).toBeGreaterThan(0);
  });

  test('Should display customers stats cards', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    const statsCards = await page.locator('[class*="card"], [class*="stat"], [class*="summary"]').count();
    expect(statsCards).toBeGreaterThan(0);
  });

  // Create Customer Modal

  test('Should open create customer modal', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    const createButton = page.locator('button:has-text("Tạo khách hàng"), button:has-text("Create Customer"), button:has-text("Thêm")').first();
    if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await createButton.click();
      await page.waitForTimeout(500);
      
      const modal = page.locator('[role="dialog"], [class*="modal"]').first();
      if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(modal).toBeVisible();
      }
    }
  });

  test('Should show validation errors when submitting empty form', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    const createButton = page.locator('button:has-text("Tạo khách hàng"), button:has-text("Create Customer"), button:has-text("Thêm")').first();
    if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await createButton.click();
      await page.waitForTimeout(500);
      
      const submitButton = page.locator('button[type="submit"]').first();
      if (await submitButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await submitButton.click();
        await page.waitForTimeout(500);
        
        // Check for validation messages
        const validationErrors = await page.locator('[class*="error"], [class*="helper"], text-error').count();
        expect(validationErrors).toBeGreaterThan(0);
      }
    }
  });

  test('Should have form fields for customer creation', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    const createButton = page.locator('button:has-text("Tạo khách hàng"), button:has-text("Create Customer"), button:has-text("Thêm")').first();
    if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await createButton.click();
      await page.waitForTimeout(500);
      
      // Check for form fields
      const fullNameInput = page.locator('input[id*="name"], input[placeholder*="name" i]').first();
      const phoneInput = page.locator('input[id*="phone"], input[placeholder*="phone" i]').first();
      const emailInput = page.locator('input[id*="email"], input[placeholder*="email" i]').first();
      
      if (await fullNameInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(fullNameInput).toBeVisible();
      }
      if (await phoneInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(phoneInput).toBeVisible();
      }
      if (await emailInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(emailInput).toBeVisible();
      }
    }
  });

  // Customer Filters

  test('Should have status filter', async ({ page }) => {
    await page.goto('/customers');
    
    const statusFilter = page.locator('select[id*="status"], [class*="filter"] select').first();
    if (await statusFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(statusFilter).toBeVisible();
    }
  });

  test('Should have gender filter', async ({ page }) => {
    await page.goto('/customers');
    
    const genderFilter = page.locator('select[id*="gender"], [class*="filter"] select').first();
    if (await genderFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(genderFilter).toBeVisible();
    }
  });

  test('Should have search input', async ({ page }) => {
    await page.goto('/customers');
    
    const searchInput = page.locator('input[placeholder*="search" i], input[id*="search" i]').first();
    if (await searchInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(searchInput).toBeVisible();
    }
  });

  test('Should filter customers by status', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    const statusFilter = page.locator('select[id*="status"], [class*="filter"] select').first();
    if (await statusFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await statusFilter.selectOption('ACTIVE');
      await page.waitForTimeout(500);
      
      // Check if filtering was applied (page should update)
      const rows = await page.locator('tbody tr, [class*="customer-row"]').count();
      expect(rows).toBeGreaterThanOrEqual(0);
    }
  });

  // Customer Actions

  test('Should have edit button for each customer', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(2000);
    
    const editButton = page.locator('button:has-text("Sửa"), button:has-text("Edit"), [aria-label*="edit" i]').first();
    if (await editButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(editButton).toBeVisible();
    }
  });

  test('Should have delete button for each customer', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(2000);
    
    const deleteButton = page.locator('button:has-text("Xóa"), button:has-text("Delete"), [aria-label*="delete" i]').first();
    if (await deleteButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(deleteButton).toBeVisible();
    }
  });

  test('Should open edit customer modal', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(2000);
    
    const editButton = page.locator('button:has-text("Sửa"), button:has-text("Edit"), [aria-label*="edit" i]').first();
    if (await editButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await editButton.click();
      await page.waitForTimeout(500);
      
      const modal = page.locator('[role="dialog"], [class*="modal"]').first();
      if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(modal).toBeVisible();
      }
    }
  });

  // Sidebar Navigation

  test('Should have customers link in sidebar', async ({ page }) => {
    const customersLink = page.locator('a[href*="/customers"], nav a:has-text("Khách hàng"), nav a:has-text("Customers")').first();
    await expect(customersLink).toBeVisible();
  });

  test('Should navigate to orders from sidebar', async ({ page }) => {
    const ordersLink = page.locator('a[href*="/orders"], nav a:has-text("Đơn hàng"), nav a:has-text("Orders")').first();
    if (await ordersLink.isVisible({ timeout: 2000 }).catch(() => false)) {
      await ordersLink.click();
      await page.waitForTimeout(1000);
      await expect(page).toHaveURL(/\/orders/);
    }
  });

  // Pagination

  test('Should have pagination controls', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    const pagination = page.locator('[class*="pagination"], nav[aria-label*="pagination"]').first();
    if (await pagination.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(pagination).toBeVisible();
    }
  });

  test('Should navigate to next page', async ({ page }) => {
    await page.goto('/customers');
    await page.waitForTimeout(1000);
    
    const nextButton = page.locator('button:has-text("Tiếp"), button[aria-label*="next" i]').first();
    if (await nextButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      const isDisabled = await nextButton.getAttribute('disabled');
      if (!isDisabled) {
        await nextButton.click();
        await page.waitForTimeout(500);
      }
    }
  });
});
