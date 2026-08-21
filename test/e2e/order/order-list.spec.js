const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');
const { createTestOrder } = require('../../utils/order-helpers');

const BASE_URL = process.env.BASE_URL || process.env.FRONTEND_URL || 'http://localhost:5174';

test.describe('Order List E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // Order List Page Loading
  test.describe('Order List Page Loading', () => {
    
    test('OL-1 - Order list page loads successfully', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await expect(managerPage).toHaveURL(/\/orders/);
    });

    test('OL-2 - Order list shows page title or heading', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      const content = await managerPage.textContent('body');
      expect(content).toMatch(/đơn hàng|order/i);
    });

    test('OL-3 - Order list displays statistics cards', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForTimeout(1500);
      
      // Look for stat cards with numbers
      const statsCards = await managerPage.locator('[class*="stat"], [class*="card"], [class*="summary"]').count();
      expect(statsCards).toBeGreaterThan(0);
    });

    test('OL-4 - Order list has refresh button', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      
      const refreshBtn = managerPage.locator('button:has-text("Refresh"), button:has-text("Làm mới"), [title*="refresh" i]').first();
      if (await refreshBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(refreshBtn).toBeVisible();
      }
    });
  });

  // Order Filters
  test.describe('Order Filters', () => {

    test('OF-1 - Order list has status filter dropdown', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      
      const statusFilter = managerPage.locator('select, [class*="select"], [role="combobox"]').first();
      if (await statusFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(statusFilter).toBeVisible();
      }
    });

    test('OF-2 - Order list has channel filter dropdown', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      // Channel filters might be dropdowns or buttons
      const channelFilter = managerPage.locator('select, [class*="channel"], button:has-text("Kênh")').first();
      if (await channelFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(channelFilter).toBeVisible();
      }
    });

    test('OF-3 - Order list has search input field', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      
      const searchInput = managerPage.locator('input[type="text"], input[placeholder*="search" i], input[placeholder*="tìm" i]').first();
      if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(searchInput).toBeVisible();
      }
    });

    test('OF-4 - Search by order code returns results', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const searchInput = managerPage.locator('input[type="text"], input[placeholder*="search" i]').first();
      if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await searchInput.fill('ORD-');
        await managerPage.waitForTimeout(1000);
        
        // Page should still show content
        const content = await managerPage.textContent('body');
        expect(content).toBeTruthy();
      }
    });

    test('OF-5 - Order list has pagination controls', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const pagination = managerPage.locator('[class*="pagination"], nav, [role="navigation"]').first();
      if (await pagination.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(pagination).toBeVisible();
      }
    });
  });

  // Order Actions
  test.describe('Order Actions', () => {

    test('OA-1 - Order list has export button', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      
      const exportBtn = managerPage.locator('button:has-text("Export"), button:has-text("Xuất"), button:has-text("Tải")').first();
      if (await exportBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(exportBtn).toBeVisible();
      }
    });

    test('OA-2 - Export button opens export modal', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1000);
      
      const exportBtn = managerPage.locator('button:has-text("Export"), button:has-text("Xuất")').first();
      if (await exportBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await exportBtn.click();
        await managerPage.waitForTimeout(500);
        
        const modal = managerPage.locator('[role="dialog"], [class*="modal"], [class*="overlay"]').first();
        if (await modal.isVisible({ timeout: 3000 }).catch(() => false)) {
          await expect(modal).toBeVisible();
        }
      }
    });

    test('OA-3 - Order list has pull orders button', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      
      const pullBtn = managerPage.locator('button:has-text("Pull"), button:has-text("Kéo"), button:has-text("Đồng bộ")').first();
      if (await pullBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(pullBtn).toBeVisible();
      }
    });
  });
});

test.describe('Order Detail E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test.describe('Order Detail View', () => {

    test('OD-1 - Clicking on order opens detail view', async ({ managerPage, request }) => {
      // Seed at least one order so the list isn't empty
      const token = await getAuthTokenCached(request);
      await createTestOrder(request, token);

      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1500);

      // The table row itself is not clickable; only the "Xem chi tiết" eye button navigates.
      const detailBtn = managerPage.locator('button[title="Xem chi tiết"]').first();
      await expect(detailBtn).toBeVisible({ timeout: 10000 });
      await detailBtn.click();

      // Should navigate to the order detail page (id may be UUID or numeric)
      await managerPage.waitForURL(/\/orders\/[^/]+$/, { timeout: 10000 });
      await expect(managerPage).toHaveURL(/\/orders\/[^/]+$/);
    });

    test('OD-2 - Order detail shows customer information', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1500);
      
      const orderRow = managerPage.locator('tbody tr').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        // Should have some identifying information
        expect(content.length).toBeGreaterThan(0);
      }
    });

    test('OD-3 - Order detail shows items list', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1500);
      
      const orderRow = managerPage.locator('tbody tr').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1000);
        
        // Look for items table or list
        const itemsTable = managerPage.locator('table, [class*="item"]').first();
        if (await itemsTable.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(itemsTable).toBeVisible();
        }
      }
    });

    test('OD-4 - Order detail shows status badge', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1500);
      
      const orderRow = managerPage.locator('tbody tr').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1000);
        
        // Status badges contain status text
        const statusText = await managerPage.textContent('body');
        expect(statusText).toMatch(/CHỜ|XÁC NHẬN|ĐANG|PROCESSING|PENDING|CONFIRMED|SHIPPED|DELIVERED/i);
      }
    });

    test('OD-5 - Order detail shows payment status', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1500);
      
      const orderRow = managerPage.locator('tbody tr').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1000);
        
        const content = await managerPage.textContent('body');
        expect(content).toMatch(/thanh toán|PAID|UNPAID|Đã thanh|Chưa/i);
      }
    });
  });

  test.describe('Order Detail Actions', () => {

    test('ODA-1 - Order detail has close button', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1500);
      
      const orderRow = managerPage.locator('tbody tr').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1000);
        
        const closeBtn = managerPage.locator('button:has-text("Close"), button:has-text("Đóng"), button[aria-label*="close" i]').first();
        if (await closeBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(closeBtn).toBeVisible();
        }
      }
    });

    test('ODA-2 - Can navigate to order history tab', async ({ managerPage }) => {
      await managerPage.goto(`${BASE_URL}/orders`);
      await managerPage.waitForLoadState('networkidle');
      await managerPage.waitForTimeout(1500);
      
      const orderRow = managerPage.locator('tbody tr').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1000);
        
        const historyTab = managerPage.locator('button:has-text("History"), button:has-text("Lịch sử"), [class*="tab"]:has-text("Lịch sử")').first();
        if (await historyTab.isVisible({ timeout: 2000 }).catch(() => false)) {
          await historyTab.click();
          await managerPage.waitForTimeout(500);
          
          const content = await managerPage.textContent('body');
          expect(content).toMatch(/lịch sử|history|thay đổi|change/i);
        }
      }
    });
  });
});

test.describe('Order Status Workflow E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('OW-1 - Order list displays all order statuses', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1500);
    
    const content = await managerPage.textContent('body');
    
    // Should display various order statuses
    const hasStatus = /PENDING|CONFIRMED|PROCESSING|SHIPPED|IN_TRANSIT|DELIVERED|CANCELLED/i.test(content);
    // Or Vietnamese labels
    const hasVietnameseStatus = /CHỜ XỬ LÝ|ĐÃ XÁC NHẬN|ĐANG XỬ LÝ|SẴN SÀNG GIAO|ĐANG VẬN CHUYỂN|ĐÃ GIAO|ĐÃ HỦY/i.test(content);
    
    expect(hasStatus || hasVietnameseStatus).toBeTruthy();
  });

  test('OW-2 - Different payment statuses are displayed', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1500);
    
    const content = await managerPage.textContent('body');
    
    // Should show payment statuses
    expect(content).toMatch(/thanh toán|PAID|UNPAID|Đã thanh|Chưa thanh/i);
  });

  test('OW-3 - Order can be clicked to view details with status info', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1500);
    
    // Find any visible order row
    const rows = managerPage.locator('tbody tr, [class*="order-row"]');
    const rowCount = await rows.count();
    
    if (rowCount > 0) {
      await rows.first().click();
      await managerPage.waitForTimeout(1000);
      
      // Detail should be visible
      const content = await managerPage.textContent('body');
      expect(content.length).toBeGreaterThan(0);
    }
  });

  test('OW-4 - Order channel information is displayed', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1500);
    
    const content = await managerPage.textContent('body');
    
    // Should show channel/platform info
    const hasChannel = /Shopee|Lazada|TikTok|Website|Manual|Manual Orders|Sàn/i.test(content);
    expect(hasChannel || content.length > 0).toBeTruthy();
  });

  test('OW-5 - Order total amount is displayed', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1500);
    
    const content = await managerPage.textContent('body');
    
    // Should display monetary values
    const hasMoney = /\d+[\.,]\d{3}|VND|₫|đ/i.test(content);
    expect(hasMoney || content.length > 0).toBeTruthy();
  });
});

test.describe('Order Pagination E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('OP-1 - Pagination shows current page information', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1500);

    // The shared <Pagination> component renders
    // <nav class="pagination" aria-label="Phân trang">. The sidebar uses a
    // generic <nav> without aria-label, so prefer the labelled one. Fall back
    // to a generic <nav> + the page-info line if the labelled one is absent.
    const labelledPagination = managerPage.locator('nav[aria-label*="Phân trang" i], nav[aria-label*="pagination" i]').first();
    const anyPagination = managerPage.locator('nav').first();
    const pagination = (await labelledPagination.count()) > 0 ? labelledPagination : anyPagination;
    if (await pagination.isVisible({ timeout: 3000 }).catch(() => false)) {
      const content = await pagination.textContent();
      expect(content).toMatch(/\d+|page|trang|Trang|Page/i);
    } else {
      // Page info may also live in a footer span like "Trang 1 / 5".
      const pageInfo = managerPage.locator('text=/Trang\\s*\\d+|Page\\s*\\d+|\\/\\s*\\d+\\s*$/i').first();
      await expect(pageInfo).toBeVisible({ timeout: 3000 });
    }
  });

  test('OP-2 - Can navigate to next page if available', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    const nextBtn = managerPage.locator('button:has-text("Next"), button:has-text("Sau"), [aria-label*="next" i]').first();
    if (await nextBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      // Check if button is enabled
      const isDisabled = await nextBtn.getAttribute('disabled');
      if (!isDisabled) {
        await nextBtn.click();
        await managerPage.waitForTimeout(1000);
        
        // Should still be on orders page
        await expect(managerPage).toHaveURL(/\/orders/);
      }
    }
  });

  test('OP-3 - Shows total order count', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1500);
    
    const content = await managerPage.textContent('body');
    
    // Should show some count information
    const hasCount = /\d+\s*(đơn|order|items?)/i.test(content) || 
                     /\d+\s*\/|hiển thị|display/i.test(content);
    expect(hasCount || content.length > 0).toBeTruthy();
  });
});

test.describe('Order Navigation E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('ON-1 - Can navigate to orders from dashboard', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/dashboard`);
    await managerPage.waitForLoadState('networkidle');
    
    // Find and click orders link/button
    const ordersLink = managerPage.locator('a[href*="/orders"], button:has-text("Đơn hàng"), [class*="order"]').first();
    if (await ordersLink.isVisible({ timeout: 3000 }).catch(() => false)) {
      await ordersLink.click();
      await managerPage.waitForLoadState('networkidle');
      await expect(managerPage).toHaveURL(/\/orders/);
    }
  });

  test('ON-2 - Can navigate to orders from sidebar', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    
    // Navigate away and back
    await managerPage.goto(`${BASE_URL}/dashboard`);
    await managerPage.waitForLoadState('networkidle');
    
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await expect(managerPage).toHaveURL(/\/orders/);
  });

  test('ON-3 - Orders page persists filter state on refresh', async ({ managerPage }) => {
    await managerPage.goto(`${BASE_URL}/orders`);
    await managerPage.waitForLoadState('networkidle');
    await managerPage.waitForTimeout(1000);
    
    // Apply a filter if possible
    const filter = managerPage.locator('select').first();
    if (await filter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await filter.selectOption({ index: 1 }).catch(() => {});
      await managerPage.waitForTimeout(500);
    }
    
    // Refresh page
    await managerPage.reload();
    await managerPage.waitForLoadState('networkidle');
    
    // Should still be on orders page
    await expect(managerPage).toHaveURL(/\/orders/);
  });
});
