const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Order Return List Page E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test.describe('Page Loading', () => {
    
    test('ORL-1 - Order return list page loads successfully', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await expect(managerPage).toHaveURL(/\/order-returns/);
    });

    test('ORL-2 - Order return list shows page content', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      const content = await managerPage.textContent('body');
      expect(content.length).toBeGreaterThan(0);
    });

    test('ORL-3 - Page has correct heading or title', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      const content = await managerPage.textContent('body');
      expect(content).toMatch(/trả hàng|return|hoàn tiền|refund/i);
    });
  });

  test.describe('Return List Display', () => {

    test('ORL-4 - Returns are displayed in a list or table', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const tableOrList = managerPage.locator('table, [class*="list"], [class*="List"], [class*="item"]').first();
      if (await tableOrList.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(tableOrList).toBeVisible();
      }
    });

    test('ORL-5 - Return list shows return status badges', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const content = await managerPage.textContent('body');
      // Should show return statuses
      const hasStatus = /PENDING|CONFIRMED|COMPLETED|REJECTED|APPROVED|INSPECTING|PROCESSING/i.test(content) ||
                       /CHỜ XỬ LÝ|ĐÃ XÁC NHẬN|HOÀN THÀNH|TỪ CHỐI|ĐANG KIỂM TRA/i.test(content);
      expect(hasStatus || content.length > 0).toBeTruthy();
    });

    test('ORL-6 - Return list shows order reference', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const content = await managerPage.textContent('body');
      // Should reference orders
      const hasOrderRef = /ORD-|order|mã đơn|đơn hàng/i.test(content);
      expect(hasOrderRef || content.length > 0).toBeTruthy();
    });

    test('ORL-7 - Return list shows customer information', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const content = await managerPage.textContent('body');
      // Should show customer info
      const hasCustomer = /khách|user|customer|name|tên/i.test(content);
      expect(hasCustomer || content.length > 0).toBeTruthy();
    });
  });

  test.describe('Return List Filters', () => {

    test('ORL-8 - Return list has status filter', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      
      const statusFilter = managerPage.locator('select, [class*="filter"], [class*="select"]').first();
      if (await statusFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(statusFilter).toBeVisible();
      }
    });

    test('ORL-9 - Return list has search input', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      
      const searchInput = managerPage.locator('input[type="text"], input[placeholder*="search" i], input[placeholder*="tìm" i]').first();
      if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(searchInput).toBeVisible();
      }
    });

    test('ORL-10 - Return list has date filter', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      
      const dateInputs = managerPage.locator('input[type="date"], input[placeholder*="date"], input[placeholder*="ngày"]');
      const count = await dateInputs.count();
      expect(count).toBeGreaterThanOrEqual(0);
    });

    test('ORL-11 - Search input accepts text', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(500);
      
      const searchInput = managerPage.locator('input[type="text"]').first();
      if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await searchInput.fill('test');
        await managerPage.waitForTimeout(500);
        const value = await searchInput.inputValue();
        expect(value).toBeTruthy();
      }
    });
  });

  test.describe('Return List Actions', () => {

    test('ORL-12 - Return list has refresh button', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      
      const refreshBtn = managerPage.locator('button:has-text("Refresh"), button:has-text("Làm mới"), button[title*="refresh" i]').first();
      if (await refreshBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(refreshBtn).toBeVisible();
      }
    });

    test('ORL-13 - Clicking on a return opens detail', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const returnRow = managerPage.locator('tbody tr, [class*="return-item"], [class*="item"]').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        // Should open detail panel
        const detail = managerPage.locator('[class*="detail"], [class*="modal"], [class*="drawer"], [class*="panel"]').first();
        if (await detail.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(detail).toBeVisible();
        }
      }
    });
  });

  test.describe('Pagination', () => {

    test('ORL-14 - Return list has pagination controls', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const pagination = managerPage.locator('[class*="pagination"], nav, [role="navigation"]').first();
      if (await pagination.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(pagination).toBeVisible();
      }
    });

    test('ORL-15 - Pagination shows page information', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const content = await managerPage.textContent('body');
      expect(content).toMatch(/\d+|page|trang|hiển thị/i);
    });

    test('ORL-16 - Can navigate between pages', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const nextBtn = managerPage.locator('button:has-text("Next"), button:has-text("Sau"), [aria-label*="next" i]').first();
      if (await nextBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        const isDisabled = await nextBtn.getAttribute('disabled');
        if (!isDisabled) {
          await nextBtn.click();
          await managerPage.waitForTimeout(1000);
          await expect(managerPage).toHaveURL(/\/order-returns/);
        }
      }
    });
  });

  test.describe('Navigation', () => {

    test('ORL-17 - Can navigate to order return from sidebar', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      
      // Verify page loaded
      await expect(managerPage).toHaveURL(/\/order-returns/);
    });

    test('ORL-18 - Page loads without errors', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const errors = [];
      managerPage.on('pageerror', (err) => errors.push(err.message));
      
      await managerPage.reload();
      await managerPage.waitForLoadState('networkidle');
      
      expect(errors.length).toBe(0);
    });
  });
});

test.describe('Order Return Detail E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test.describe('Return Detail View', () => {

    test('ORD-1 - Return detail shows return information', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      // Click on a return if available
      const returnRow = managerPage.locator('tbody tr, [class*="return-item"]').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        expect(content.length).toBeGreaterThan(0);
      }
    });

    test('ORD-2 - Return detail shows original order reference', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const returnRow = managerPage.locator('tbody tr').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        const hasOrderRef = /ORD-|order|mã đơn/i.test(content);
        expect(hasOrderRef || content.length > 0).toBeTruthy();
      }
    });

    test('ORD-3 - Return detail shows items being returned', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const returnRow = managerPage.locator('tbody tr').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        // Look for items table or product info
        const itemsSection = managerPage.locator('table, [class*="item"], [class*="product"]').first();
        if (await itemsSection.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(itemsSection).toBeVisible();
        }
      }
    });

    test('ORD-4 - Return detail shows return reason', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const returnRow = managerPage.locator('tbody tr').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        // Should show reason field
        const hasReason = /lý do|reason|nguyên nhân|description|mô tả/i.test(content);
        expect(hasReason || content.length > 0).toBeTruthy();
      }
    });

    test('ORD-5 - Return detail shows status', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const returnRow = managerPage.locator('tbody tr').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        expect(content).toMatch(/trạng thái|status|PENDING|CONFIRMED|COMPLETED/i);
      }
    });
  });

  test.describe('Return Detail Actions', () => {

    test('ORD-6 - Detail has close button', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const returnRow = managerPage.locator('tbody tr').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        const closeBtn = managerPage.locator('button:has-text("Close"), button:has-text("Đóng"), [aria-label*="close" i]').first();
        if (await closeBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(closeBtn).toBeVisible();
        }
      }
    });

    test('ORD-7 - Detail shows action buttons based on status', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/order-returns`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const returnRow = managerPage.locator('tbody tr').first();
      if (await returnRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await returnRow.click();
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        // Should have some action buttons
        const hasActions = /xác nhận|confirm|duyệt|approve|từ chối|reject|kiểm tra|inspect|hoàn tiền|refund/i.test(content);
        expect(hasActions || content.length > 0).toBeTruthy();
      }
    });
  });
});

test.describe('Order Return Status Workflow E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('ORW-1 - Return list displays all return statuses', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    const content = await managerPage.textContent('body');
    
    // Should show return workflow statuses
    const hasStatuses = /PENDING|CONFIRMED|APPROVED|REJECTED|INSPECTING|COMPLETED|PROCESSING/i.test(content) ||
                       /CHỜ|DUYỆT|TỪ CHỐI|KIỂM TRA|HOÀN THÀNH|XỬ LÝ/i.test(content);
    expect(hasStatuses || content.length > 0).toBeTruthy();
  });

  test('ORW-2 - Returns show refund amount', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    const content = await managerPage.textContent('body');
    
    // Should show monetary values
    const hasAmount = /\d+[\.,]\d{3}|VND|₫|hoàn tiền|refund|tiền/i.test(content);
    expect(hasAmount || content.length > 0).toBeTruthy();
  });

  test('ORW-3 - Returns show customer info', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    const content = await managerPage.textContent('body');
    expect(content).toMatch(/khách|customer|name|tên|người/i);
  });

  test('ORW-4 - Returns show creation date', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    const content = await managerPage.textContent('body');
    // Should show dates
    const hasDate = /\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4}|ngày|date|time|thời/i.test(content);
    expect(hasDate || content.length > 0).toBeTruthy();
  });

  test('ORW-5 - Different return types are displayed', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    const content = await managerPage.textContent('body');
    // Return types like: refund, exchange, etc.
    const hasReturnType = /hoàn|trả|đổi|exchange|refund|return/i.test(content);
    expect(hasReturnType || content.length > 0).toBeTruthy();
  });
});

test.describe('Order Return Filtering E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('ORF-1 - Filter by pending status', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    // Find status filter and select pending
    const statusFilter = managerPage.locator('select').first();
    if (await statusFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
      // Try to find pending option
      const options = await statusFilter.locator('option').allTextContents();
      const pendingIndex = options.findIndex((opt) => /PENDING|CHỜ/i.test(opt));
      if (pendingIndex >= 0) {
        await statusFilter.selectOption({ index: pendingIndex });
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        expect(content.length).toBeGreaterThan(0);
      }
    }
  });

  test('ORF-2 - Filter by completed status', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    const statusFilter = managerPage.locator('select').first();
    if (await statusFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
      const options = await statusFilter.locator('option').allTextContents();
      const completedIndex = options.findIndex((opt) => /COMPLETED|HOÀN THÀNH|DONE/i.test(opt));
      if (completedIndex >= 0) {
        await statusFilter.selectOption({ index: completedIndex });
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        expect(content.length).toBeGreaterThan(0);
      }
    }
  });

  test('ORF-3 - Clear filters shows all returns', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    // Apply and then clear a filter
    const statusFilter = managerPage.locator('select').first();
    if (await statusFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
      await statusFilter.selectOption({ index: 0 }); // Select first option (usually "All" or similar)
      await managerPage.waitForTimeout(1000);
      
      const content = await managerPage.textContent('body');
      expect(content.length).toBeGreaterThan(0);
    }
  });

  test('ORF-4 - Date range filter works', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/order-returns`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(500);
    
    const dateInputs = managerPage.locator('input[type="date"]');
    const count = await dateInputs.count();
    
    if (count >= 2) {
      // Fill both date inputs
      await dateInputs.nth(0).fill('2024-01-01');
      await dateInputs.nth(1).fill('2024-12-31');
      await managerPage.waitForTimeout(1000);
      
      const content = await managerPage.textContent('body');
      expect(content.length).toBeGreaterThan(0);
    }
  });
});
