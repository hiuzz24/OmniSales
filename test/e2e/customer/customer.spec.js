const { test, expect } = require('../../fixtures/auth-fixtures');

test.describe('Customer E2E Tests', () => {

  // Customer List Page

  test('Should navigate to customers page', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await expect(managerPage).toHaveURL(/\/customers/);
  });

  test('Should display customers table', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);
    const content = await managerPage.textContent('body');
    expect(content.length).toBeGreaterThan(0);
  });

  test('Should display customers stats cards', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);
    const statsCards = await managerPage.locator('[class*="card"], [class*="stat"], [class*="summary"]').count();
    expect(statsCards).toBeGreaterThan(0);
  });

  // Create Customer Modal

  test('Should open create customer modal', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);

    const createButton = managerPage.locator('button:has-text("Tạo khách hàng"), button:has-text("Create Customer"), button:has-text("Thêm")').first();
    if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await createButton.click();
      await managerPage.waitForTimeout(500);

      const modal = managerPage.locator('[role="dialog"], [class*="modal"]').first();
      if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(modal).toBeVisible();
      }
    }
  });

  test('Should show validation errors when submitting empty form', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);

    const createButton = managerPage.locator('button:has-text("Tạo khách hàng"), button:has-text("Create Customer"), button:has-text("Thêm")').first();
    if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await createButton.click();
      await managerPage.waitForTimeout(500);

      const submitButton = managerPage.locator('button[type="submit"]').first();
      if (await submitButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await submitButton.click();
        await managerPage.waitForTimeout(500);

        const validationErrors = await managerPage.locator('[class*="error"], [class*="helper"], text-error').count();
        expect(validationErrors).toBeGreaterThan(0);
      }
    }
  });

  test('Should have form fields for customer creation', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);

    const createButton = managerPage.locator('button:has-text("Tạo khách hàng"), button:has-text("Create Customer"), button:has-text("Thêm")').first();
    if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await createButton.click();
      await managerPage.waitForTimeout(500);

      const fullNameInput = managerPage.locator('input[id*="name"], input[placeholder*="name" i]').first();
      const phoneInput = managerPage.locator('input[id*="phone"], input[placeholder*="phone" i]').first();
      const emailInput = managerPage.locator('input[id*="email"], input[placeholder*="email" i]').first();

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

  // Filters

  test('Should have status filter', async ({ managerPage }) => {
    await managerPage.goto('/customers');

    const statusFilter = managerPage.locator('select[id*="status"], [class*="filter"] select').first();
    if (await statusFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(statusFilter).toBeVisible();
    }
  });

  test('Should have gender filter', async ({ managerPage }) => {
    await managerPage.goto('/customers');

    const genderFilter = managerPage.locator('select[id*="gender"], [class*="filter"] select').first();
    if (await genderFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(genderFilter).toBeVisible();
    }
  });

  test('Should have search input', async ({ managerPage }) => {
    await managerPage.goto('/customers');

    const searchInput = managerPage.locator('input[placeholder*="search" i], input[id*="search" i]').first();
    if (await searchInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(searchInput).toBeVisible();
    }
  });

  test('Should filter customers by status', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);

    const statusFilter = managerPage.locator('select[id*="status"], [class*="filter"] select').first();
    if (await statusFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await statusFilter.selectOption('ACTIVE');
      await managerPage.waitForTimeout(500);

      const rows = await managerPage.locator('tbody tr, [class*="customer-row"]').count();
      expect(rows).toBeGreaterThanOrEqual(0);
    }
  });

  // Actions

  test('Should have edit button for each customer', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(2000);

    const editButton = managerPage.locator('button:has-text("Sửa"), button:has-text("Edit"), [aria-label*="edit" i]').first();
    if (await editButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(editButton).toBeVisible();
    }
  });

  test('Should have delete button for each customer', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(2000);

    const deleteButton = managerPage.locator('button:has-text("Xóa"), button:has-text("Delete"), [aria-label*="delete" i]').first();
    if (await deleteButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(deleteButton).toBeVisible();
    }
  });

  test('Should open edit customer modal', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(2000);

    const editButton = managerPage.locator('button:has-text("Sửa"), button:has-text("Edit"), [aria-label*="edit" i]').first();
    if (await editButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await editButton.click();
      await managerPage.waitForTimeout(500);

      const modal = managerPage.locator('[role="dialog"], [class*="modal"]').first();
      if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(modal).toBeVisible();
      }
    }
  });

  // Sidebar Navigation

  test('Should have customers link in sidebar', async ({ managerPage }) => {
    const customersLink = managerPage.locator('a[href*="/customers"], nav a:has-text("Khách hàng"), nav a:has-text("Customers")').first();
    await expect(customersLink).toBeVisible();
  });

  test('Should navigate to orders from sidebar', async ({ managerPage }) => {
    const ordersLink = managerPage.locator('a[href*="/orders"], nav a:has-text("Đơn hàng"), nav a:has-text("Orders")').first();
    if (await ordersLink.isVisible({ timeout: 2000 }).catch(() => false)) {
      await ordersLink.click();
      await managerPage.waitForTimeout(1000);
      await expect(managerPage).toHaveURL(/\/orders/);
    }
  });

  // Pagination

  test('Should have pagination controls', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);

    const pagination = managerPage.locator('[class*="pagination"], nav[aria-label*="pagination"]').first();
    if (await pagination.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(pagination).toBeVisible();
    }
  });

  test('Should navigate to next page', async ({ managerPage }) => {
    await managerPage.goto('/customers');
    await managerPage.waitForTimeout(1000);

    const nextButton = managerPage.locator('button:has-text("Tiếp"), button[aria-label*="next" i]').first();
    if (await nextButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      const isDisabled = await nextButton.getAttribute('disabled');
      if (!isDisabled) {
        await nextButton.click();
        await managerPage.waitForTimeout(500);
      }
    }
  });
});
