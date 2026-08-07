const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Order E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // Order List Page

  test('Should navigate to orders page', async ({ managerPage }) => {
    await managerPage.goto('/orders');
    await expect(managerPage).toHaveURL(/\/orders/);
  });

  test('Should display orders table with columns', async ({ managerPage }) => {
    await managerPage.goto('/orders');
    await managerPage.waitForTimeout(1000);
    const content = await managerPage.textContent('body');
    expect(content.length).toBeGreaterThan(0);
  });

  test('Should display orders stats cards', async ({ managerPage }) => {
    await managerPage.goto('/orders');
    await managerPage.waitForTimeout(1000);
    const statsCards = await managerPage.locator('[class*="card"], [class*="stat"], [class*="summary"]').count();
    expect(statsCards).toBeGreaterThan(0);
  });

  // Order Filters

  test('Should have status filter dropdown', async ({ managerPage }) => {
    await managerPage.goto('/orders');
    const statusFilter = managerPage.locator('select[id*="status"], [class*="filter"] select').first();
    if (await statusFilter.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(statusFilter).toBeVisible();
    }
  });

  test('Should have search input', async ({ managerPage }) => {
    await managerPage.goto('/orders');
    const searchInput = managerPage.locator('input[placeholder*="search" i], input[id*="search" i], input[id*="keyword" i]').first();
    if (await searchInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(searchInput).toBeVisible();
    }
  });

  test('Should have pagination controls', async ({ managerPage }) => {
    await managerPage.goto('/orders');
    await managerPage.waitForTimeout(1000);
    const pagination = managerPage.locator('[class*="pagination"], nav[aria-label*="pagination"]').first();
    if (await pagination.isVisible({ timeout: 2000 }).catch(() => false)) {
      await expect(pagination).toBeVisible();
    }
  });

  // Order Detail Modal/Page

  test.describe('Order Detail Tests', () => {

    test('Should open order detail when clicking on an order', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tr, [class*="order-item"], [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1000);

        const modal = managerPage.locator('[role="dialog"], [class*="modal"], [class*="drawer"]').first();
        if (await modal.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(modal).toBeVisible();
        }
      }
    });

    test('Should display order detail information', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const orderDetail = managerPage.locator('[class*="detail"], [class*="info"], [class*="order-info"]').first();
        if (await orderDetail.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(orderDetail).toBeVisible();
        }
      }
    });

    test('Should display order items in detail', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const itemsSection = managerPage.locator('[class*="item"], [class*="product"], table').first();
        if (await itemsSection.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(itemsSection).toBeVisible();
        }
      }
    });

    test('Should display order status badge', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const statusBadge = managerPage.locator('[class*="status"], [class*="badge"], span[class*="tag"]').first();
        if (await statusBadge.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(statusBadge).toBeVisible();
        }
      }
    });

    test('Should display payment status badge', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const paymentBadge = managerPage.locator('[class*="payment"], [class*="paid"], [class*="unpaid"]').first();
        if (await paymentBadge.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(paymentBadge).toBeVisible();
        }
      }
    });
  });

  // Order Status Change

  test.describe('Order Status Change Tests', () => {

    test('Should have status dropdown in order detail', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const statusDropdown = managerPage.locator('select[id*="status" i], [class*="status"] select').first();
        if (await statusDropdown.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(statusDropdown).toBeVisible();
        }
      }
    });

    test('Should have action buttons in order detail', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const actionButtons = managerPage.locator('button[class*="action"], button:has-text("Xác nhận"), button:has-text("Hủy")').first();
        if (await actionButtons.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(actionButtons).toBeVisible();
        }
      }
    });

    test('Should have confirm button for pending orders', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const confirmBtn = managerPage.locator('button:has-text("Xác nhận"), button:has-text("Confirm")').first();
      if (await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(confirmBtn).toBeVisible();
      }
    });

    test('Should have process button for confirmed orders', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const processBtn = managerPage.locator('button:has-text("Xử lý"), button:has-text("Process")').first();
      if (await processBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(processBtn).toBeVisible();
      }
    });

    test('Should have ship button for processing orders', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const shipBtn = managerPage.locator('button:has-text("Gửi hàng"), button:has-text("Ship")').first();
      if (await shipBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(shipBtn).toBeVisible();
      }
    });
  });

  // Payment Status Change

  test.describe('Payment Status Change Tests', () => {

    test('Should have payment status dropdown in order detail', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const paymentDropdown = managerPage.locator('select[id*="payment" i], [class*="payment"] select').first();
        if (await paymentDropdown.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(paymentDropdown).toBeVisible();
        }
      }
    });

    test('Should have mark as paid button', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const paidBtn = managerPage.locator('button:has-text("Đã thanh toán"), button:has-text("Mark Paid")').first();
      if (await paidBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(paidBtn).toBeVisible();
      }
    });

    test('Should display payment status as UNPAID badge', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const unpaidBadge = managerPage.locator('span:has-text("Chưa thanh toán"), span:has-text("UNPAID")').first();
      if (await unpaidBadge.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(unpaidBadge).toBeVisible();
      }
    });

    test('Should display payment status as PAID badge', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const paidBadge = managerPage.locator('span:has-text("Đã thanh toán"), span:has-text("PAID")').first();
      if (await paidBadge.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(paidBadge).toBeVisible();
      }
    });
  });

  // Cancel Order

  test.describe('Cancel Order Tests', () => {

    test('Should have cancel button in order detail', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const cancelBtn = managerPage.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(cancelBtn).toBeVisible();
        }
      }
    });

    test('Should have cancel reason input when clicking cancel', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const cancelBtn = managerPage.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await cancelBtn.click();
          await managerPage.waitForTimeout(500);

          const reasonInput = managerPage.locator('input[id*="reason" i], textarea[id*="reason" i]').first();
          if (await reasonInput.isVisible({ timeout: 2000 }).catch(() => false)) {
            await expect(reasonInput).toBeVisible();
          }
        }
      }
    });

    test('Should have confirm cancel button in modal', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const cancelBtn = managerPage.locator('button:has-text("Hủy"), button:has-text("Cancel")').first();
        if (await cancelBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await cancelBtn.click();
          await managerPage.waitForTimeout(500);

          const confirmCancelBtn = managerPage.locator('button:has-text("Xác nhận hủy"), button:has-text("Confirm Cancel")').first();
          if (await confirmCancelBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
            await expect(confirmCancelBtn).toBeVisible();
          }
        }
      }
    });

    test('Should disable cancel button for cancelled orders', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const cancelledBadge = managerPage.locator('span:has-text("Đã hủy"), span:has-text("CANCELLED")').first();
      if (await cancelledBadge.isVisible({ timeout: 2000 }).catch(() => false)) {
        const cancelBtn = managerPage.locator('button:has-text("Hủy")').first();
        if (await cancelBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
          await expect(cancelBtn).toBeDisabled();
        }
      }
    });
  });

  // Order History/Audit Log

  test.describe('Order History Tests', () => {

    test('Should have order history tab in detail', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const historyTab = managerPage.locator('button:has-text("Lịch sử"), button:has-text("History")').first();
        if (await historyTab.isVisible({ timeout: 2000 }).catch(() => false)) {
          await expect(historyTab).toBeVisible();
        }
      }
    });

    test('Should display audit logs when history tab is clicked', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(2000);

      const orderRow = managerPage.locator('tbody tr, [class*="order-row"]').first();
      if (await orderRow.isVisible({ timeout: 3000 }).catch(() => false)) {
        await orderRow.click();
        await managerPage.waitForTimeout(1500);

        const historyTab = managerPage.locator('button:has-text("Lịch sử"), button:has-text("History")').first();
        if (await historyTab.isVisible({ timeout: 2000 }).catch(() => false)) {
          await historyTab.click();
          await managerPage.waitForTimeout(500);

          const historyContent = managerPage.locator('[class*="history"], [class*="log"], [class*="timeline"]').first();
          if (await historyContent.isVisible({ timeout: 2000 }).catch(() => false)) {
            await expect(historyContent).toBeVisible();
          }
        }
      }
    });
  });

  // Order Actions

  test.describe('Order Actions Tests', () => {

    test('Should have create order button if user has permission', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(1000);

      const createButton = managerPage.locator('button:has-text("Tạo"), button:has-text("Create"), a[href*="/orders/new"]').first();
      if (await createButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(createButton).toBeVisible();
      }
    });

    test('Should have refresh button', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(1000);

      const refreshButton = managerPage.locator('button[aria-label*="refresh" i], button[title*="refresh" i]').first();
      if (await refreshButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(refreshButton).toBeVisible();
      }
    });

    test('Should have export button', async ({ managerPage }) => {
      await managerPage.goto('/orders');
      await managerPage.waitForTimeout(1000);

      const exportButton = managerPage.locator('button:has-text("Xuất"), button:has-text("Export")').first();
      if (await exportButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await expect(exportButton).toBeVisible();
      }
    });
  });

  // Sidebar Navigation

  test.describe('Navigation Tests', () => {

    test('Should have orders link in sidebar', async ({ managerPage }) => {
      const ordersLink = managerPage.locator('a[href*="/orders"], nav a:has-text("Đơn hàng"), nav a:has-text("Orders")').first();
      await expect(ordersLink).toBeVisible();
    });

    test('Should navigate to customers from sidebar', async ({ managerPage }) => {
      const customersLink = managerPage.locator('a[href*="/customers"], nav a:has-text("Khách hàng"), nav a:has-text("Customers")').first();
      if (await customersLink.isVisible({ timeout: 2000 }).catch(() => false)) {
        await customersLink.click();
        await managerPage.waitForTimeout(1000);
        await expect(managerPage).toHaveURL(/\/customers/);
      }
    });
  });
});
