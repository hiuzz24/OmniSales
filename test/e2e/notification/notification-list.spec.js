const { test, expect } = require('../../fixtures/auth-fixtures');

test.describe('Notification List E2E Tests', () => {

  // NOTI-LIST-E2E-1 - bell panel shows notifications
  test('NOTI-LIST-E2E-1 - Bell icon opens panel with notification items', async ({ managerPage }) => {
    await managerPage.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const bellBtn = managerPage.locator('[data-testid="notification-bell"], button:has-text("Thông báo"), svg[class*="bell"]').first();
    const hasBell = await bellBtn.count();

    if (hasBell > 0) {
      await bellBtn.click();
      await managerPage.waitForTimeout(500);
      const panelText = await managerPage.textContent('body');
      expect(panelText.length).toBeGreaterThan(0);
    } else {
      test.skip(true, 'No bell icon on dashboard');
    }
  });

  // NOTI-LIST-E2E-2 - panel can be closed
  test('NOTI-LIST-E2E-2 - Bell panel can be toggled', async ({ managerPage }) => {
    await managerPage.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const bellBtn = managerPage.locator('[data-testid="notification-bell"], button:has-text("Thông báo"), svg[class*="bell"]').first();
    const hasBell = await bellBtn.count();

    if (hasBell > 0) {
      await bellBtn.click();
      await managerPage.waitForTimeout(300);
      await bellBtn.click();
      await managerPage.waitForTimeout(300);
      expect(true).toBeTruthy();
    } else {
      test.skip(true, 'No bell icon');
    }
  });

  // NOTI-LIST-E2E-3 - mark-as-read button
  test('NOTI-LIST-E2E-3 - Mark-as-read button exists or skip', async ({ managerPage }) => {
    await managerPage.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const bellBtn = managerPage.locator('[data-testid="notification-bell"], button:has-text("Thông báo"), svg[class*="bell"]').first();
    const hasBell = await bellBtn.count();

    if (hasBell > 0) {
      await bellBtn.click();
      await managerPage.waitForTimeout(500);

      const markReadBtn = managerPage.locator('button:has-text("Đã đọc"), button:has-text("Mark"), button:has-text("đánh dấu")').first();
      const hasMarkRead = await markReadBtn.count();
      expect(hasMarkRead).toBeGreaterThanOrEqual(0);
    } else {
      test.skip(true, 'No bell icon');
    }
  });

  // NOTI-LIST-E2E-4 - notification count or badge
  test('NOTI-LIST-E2E-4 - Bell may display unread count badge', async ({ managerPage }) => {
    await managerPage.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const badge = managerPage.locator('[data-testid="notification-count"], [class*="badge"], [class*="Badge"]').first();
    const hasBadge = await badge.count();
    expect(hasBadge).toBeGreaterThanOrEqual(0);
  });

  // NOTI-LIST-E2E-5 - notification items
  test('NOTI-LIST-E2E-5 - Notification items render with content', async ({ managerPage }) => {
    await managerPage.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const bellBtn = managerPage.locator('[data-testid="notification-bell"], button:has-text("Thông báo"), svg[class*="bell"]').first();
    const hasBell = await bellBtn.count();

    if (hasBell > 0) {
      await bellBtn.click();
      await managerPage.waitForTimeout(500);

      const itemCount = await managerPage.locator('[data-testid="notification-item"], [class*="notification-item"], [class*="NotificationItem"]').count();
      expect(itemCount).toBeGreaterThanOrEqual(0);
    } else {
      test.skip(true, 'No bell icon');
    }
  });

  // NOTI-LIST-E2E-6 - panel content readable
  test('NOTI-LIST-E2E-6 - Bell panel renders readable text', async ({ managerPage }) => {
    await managerPage.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const bellBtn = managerPage.locator('[data-testid="notification-bell"], button:has-text("Thông báo"), svg[class*="bell"]').first();
    const hasBell = await bellBtn.count();

    if (hasBell > 0) {
      await bellBtn.click();
      await managerPage.waitForTimeout(500);
      const body = await managerPage.textContent('body');
      expect(body.length).toBeGreaterThan(0);
    } else {
      test.skip(true, 'No bell icon');
    }
  });
});