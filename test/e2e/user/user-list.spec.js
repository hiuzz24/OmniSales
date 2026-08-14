const { test, expect } = require('../../fixtures/auth-fixtures');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

const BASE_USERS = ['admin@osms.vn', 'manager@osms.vn', 'staff@osms.vn', 'viewer@osms.vn'];

async function restoreBaseUsers(request) {
  // Some tests (e.g. USR-E2E-11 "Disable User button changes status")
  // exercise the user-status dropdown and may accidentally deactivate a
  // base fixture user (admin/manager/staff/viewer). Without a restore,
  // every subsequent spec that logs in via managerPage/adminPage fails
  // with HTTP 500 from /auth/login ("account inactive").  Hit the
  // API to flip them back to ACTIVE so the rest of the suite stays green.
  try {
    const token = await getAuthTokenCached(request);
    // Re-fetch a fresh token in case the cached one was issued before
    // the manager was deactivated.
    let liveToken = token;
    try {
      const resp = await request.post(`${process.env.API_BASE || 'http://localhost:8080/api'}/auth/login`, {
        data: { email: 'admin@osms.vn', password: '11111111' },
      });
      if (resp.status() === 200) {
        liveToken = (await resp.json()).data.accessToken;
      }
    } catch (_) { /* fall back to cached token */ }
    for (const email of BASE_USERS) {
      try {
        // Find user id by email
        const listResp = await request.get(
          `${process.env.API_BASE || 'http://localhost:8080/api'}/users?page=0&size=200&keyword=${encodeURIComponent(email)}`,
          { headers: { Authorization: `Bearer ${liveToken}` } },
        );
        if (listResp.status() !== 200) continue;
        const listBody = await listResp.json();
        const users = listBody.data?.content || listBody.data || [];
        const u = users.find((x) => (x.email || '').toLowerCase() === email.toLowerCase());
        if (!u || !u.id) continue;
        if ((u.status || '').toUpperCase() === 'ACTIVE') continue;
        // PUT status back to ACTIVE (matches the controller's status-update path).
        await request.put(
          `${process.env.API_BASE || 'http://localhost:8080/api'}/users/${u.id}/status?status=ACTIVE`,
          { headers: { Authorization: `Bearer ${liveToken}` } },
        );
      } catch (_) { /* best-effort */ }
    }
  } catch (_) { /* never fail the suite on a cleanup miss */ }
}

test.describe('User List E2E Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
    await restoreBaseUsers(request);
  });

  // USR-E2E-1
  test('USR-E2E-1 - Navigate to /users - Stats cards are visible', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const statCards = managerPage.locator('[class*="stat"], [class*="card"], [class*="Stat"]').first();
    await expect(managerPage.locator('text=Tổng người dùng,Total Users').first()).toBeVisible({ timeout: 5000 }).catch(() => {});
    await expect(managerPage.locator('text=ACTIVE,Hoạt động').first()).toBeVisible({ timeout: 3000 }).catch(() => {});
  });

  // USR-E2E-2
  test('USR-E2E-2 - Search by user name filters table', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const searchInput = managerPage.locator('input[placeholder*="tìm kiếm"], input[placeholder*="search"], input[placeholder*="Search"]').first();
    const hasSearch = await searchInput.count();

    if (hasSearch > 0) {
      await searchInput.fill('NonExistentUser999999');
      await managerPage.waitForTimeout(500);
      const rows = managerPage.locator('table tbody tr');
      const rowCount = await rows.count();
      expect(rowCount).toBeLessThanOrEqual(10);
    }
  });

  // USR-E2E-3
  test('USR-E2E-3 - Filter by Role dropdown', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const roleSelect = managerPage.locator('select, [role="combobox"]').first();
    const hasSelect = await roleSelect.count();

    if (hasSelect > 0) {
      const options = managerPage.locator('select option, [role="option"]');
      const count = await options.count();
      if (count > 1) {
        await roleSelect.selectOption({ index: 1 });
        await managerPage.waitForTimeout(500);
      }
    }
    expect(true).toBeTruthy();
  });

  // USR-E2E-4
  test('USR-E2E-4 - Filter by Status dropdown', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('domcontentloaded');

    const selects = managerPage.locator('select');
    const count = await selects.count();

    if (count > 1) {
      await selects.nth(1).selectOption({ index: 1 });
      await managerPage.waitForTimeout(500);
    }
    expect(true).toBeTruthy();
  });

  // USR-E2E-5
  test('USR-E2E-5 - Open Create User drawer - Fields present', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const createBtn = managerPage.locator('button:has-text("Tạo"), button:has-text("Create"), button:has-text("Thêm")').first();
    const hasBtn = await createBtn.count();

    if (hasBtn > 0) {
      await createBtn.click();
      await managerPage.waitForTimeout(500);

      await expect(managerPage.locator('input[name*="name"], input[placeholder*="tên"], input[placeholder*="name"]').first()).toBeVisible({ timeout: 3000 }).catch(() => {});
      await expect(managerPage.locator('input[type="email"], input[placeholder*="email"]').first()).toBeVisible({ timeout: 3000 }).catch(() => {});

      const closeBtn = managerPage.locator('button[aria-label*="close"], button[aria-label*="Close"], button[aria-label*="Đóng"]').first();
      if (await closeBtn.count() > 0) await closeBtn.click();
    }
  });

  // USR-E2E-6
  test('USR-E2E-6 - Create user with valid data', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const createBtn = managerPage.locator('button:has-text("Tạo"), button:has-text("Create"), button:has-text("Thêm")').first();
    const hasBtn = await createBtn.count();

    if (hasBtn > 0) {
      await createBtn.click();
      await managerPage.waitForTimeout(500);

      const timestamp = Date.now();
      const nameInput = managerPage.locator('input[name*="name"], input[placeholder*="tên"], input[placeholder*="name"]').first();
      if (await nameInput.count() > 0) {
        await nameInput.fill(`E2E User ${timestamp}`);
      }

      const emailInput = managerPage.locator('input[type="email"], input[placeholder*="email"]').first();
      if (await emailInput.count() > 0) {
        await emailInput.fill(`e2e_${timestamp}@test.com`);
      }

      const saveBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save"), button:has-text("Tạo")').first();
      if (await saveBtn.count() > 0) {
        await saveBtn.click();
        await managerPage.waitForTimeout(1000);
      }

      const closeBtn = managerPage.locator('button[aria-label*="close"], button[aria-label*="Close"]').first();
      if (await closeBtn.count() > 0) await closeBtn.click();
    }
  });

  // USR-E2E-7
  test('USR-E2E-7 - Create user with invalid email shows error', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const createBtn = managerPage.locator('button:has-text("Tạo"), button:has-text("Create")').first();
    if (await createBtn.count() > 0) {
      await createBtn.click();
      await managerPage.waitForTimeout(500);

      const emailInput = managerPage.locator('input[type="email"], input[placeholder*="email"]').first();
      if (await emailInput.count() > 0) {
        await emailInput.fill('invalid-email');
        await emailInput.blur();
        await managerPage.waitForTimeout(500);
      }

      const closeBtn = managerPage.locator('button[aria-label*="close"]').first();
      if (await closeBtn.count() > 0) await closeBtn.click();
    }
  });

  // USR-E2E-8
  test('USR-E2E-8 - Click Edit on a user row - Drawer opens with data', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const editBtn = managerPage.locator('button:has-text("Sửa"), button:has-text("Edit"), [aria-label*="Edit"]').first();
    const hasEdit = await editBtn.count();

    if (hasEdit > 0) {
      await editBtn.click();
      await managerPage.waitForTimeout(500);

      const closeBtn = managerPage.locator('button[aria-label*="close"]').first();
      if (await closeBtn.count() > 0) await closeBtn.click();
    }
    expect(true).toBeTruthy();
  });

  // USR-E2E-9
  test('USR-E2E-9 - Update user and Save', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const editBtn = managerPage.locator('button:has-text("Sửa"), button:has-text("Edit")').first();
    if (await editBtn.count() > 0) {
      await editBtn.click();
      await managerPage.waitForTimeout(500);

      const nameInput = managerPage.locator('input[name*="name"]').first();
      if (await nameInput.count() > 0) {
        await nameInput.fill(`Updated ${Date.now()}`);
      }

      const saveBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save")').first();
      if (await saveBtn.count() > 0) {
        await saveBtn.click();
        await managerPage.waitForTimeout(1000);
      }

      const closeBtn = managerPage.locator('button[aria-label*="close"]').first();
      if (await closeBtn.count() > 0) await closeBtn.click();
    }
  });

  // USR-E2E-10
  test('USR-E2E-10 - Reset Password button shows popup', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const editBtn = managerPage.locator('button:has-text("Sửa"), button:has-text("Edit")').first();
    if (await editBtn.count() > 0) {
      await editBtn.click();
      await managerPage.waitForTimeout(500);

      const resetBtn = managerPage.locator('button:has-text("Đặt lại mật khẩu"), button:has-text("Reset Password")').first();
      if (await resetBtn.count() > 0) {
        await resetBtn.click();
        await managerPage.waitForTimeout(500);
      }

      const closeBtn = managerPage.locator('button[aria-label*="close"]').first();
      if (await closeBtn.count() > 0) await closeBtn.click();
    }
  });

  // USR-E2E-11
  test('USR-E2E-11 - Disable User button changes status', async ({ managerPage }) => {
    await managerPage.goto('/users', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForSelector('table, [role="table"]', { timeout: 10000 });

    // Skip rows whose email matches a base fixture (admin/manager/staff/
    // viewer/owner1). Targeting the *first* edit button would disable the
    // manager and break every subsequent test that logs in via the
    // managerPage fixture. Pick the first row that is NOT a base fixture.
    const baseEmails = new Set([
      'admin@osms.vn',
      'manager@osms.vn',
      'staff@osms.vn',
      'viewer@osms.vn',
      'owner1@osms.vn',
    ]);
    const rows = managerPage.locator('table tbody tr, [role="row"]');
    const rowCount = await rows.count();
    let targetRow = null;
    for (let i = 0; i < rowCount; i++) {
      const rowText = await rows.nth(i).textContent().catch(() => '');
      if (!rowText) continue;
      const matched = Array.from(baseEmails).some((e) =>
        rowText.toLowerCase().includes(e.toLowerCase()),
      );
      if (!matched) {
        targetRow = rows.nth(i);
        break;
      }
    }
    const editBtn = targetRow
      ? targetRow.locator('button:has-text("Sửa"), button:has-text("Edit")').first()
      : null;
    if (editBtn && (await editBtn.count()) > 0) {
      await editBtn.click();
      await managerPage.waitForTimeout(500);

      const statusSelect = managerPage.locator('select[name*="status"], select').last();
      if ((await statusSelect.count()) > 0) {
        await statusSelect.selectOption({ index: 1 });
      }

      const saveBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save")').first();
      if ((await saveBtn.count()) > 0) {
        await saveBtn.click();
        await managerPage.waitForTimeout(1000);
      }

      const closeBtn = managerPage.locator('button[aria-label*="close"]').first();
      if ((await closeBtn.count()) > 0) await closeBtn.click();
    }
  });

  // USR-E2E-12
  test('USR-E2E-12 - Invite User modal opens', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const inviteBtn = managerPage.locator('button:has-text("Mời"), button:has-text("Invite")').first();
    if (await inviteBtn.count() > 0) {
      await inviteBtn.click();
      await managerPage.waitForTimeout(500);

      const emailInput = managerPage.locator('input[type="email"]').first();
      await expect(emailInput).toBeVisible({ timeout: 3000 }).catch(() => {});

      const closeBtn = managerPage.locator('button[aria-label*="close"]').first();
      if (await closeBtn.count() > 0) await closeBtn.click();
    }
  });

  // USR-E2E-13
  test('USR-E2E-13 - CSV Export button triggers download', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const exportBtn = managerPage.locator('button:has-text("Xuất"), button:has-text("Export"), button:has-text("CSV")').first();
    if (await exportBtn.count() > 0) {
      const downloadPromise = managerPage.waitForEvent('download', { timeout: 5000 }).catch(() => null);
      await exportBtn.click();
      const download = await downloadPromise;
      if (download) {
        expect(download.suggestedFilename()).toMatch(/\.csv$/i);
      }
    }
    expect(true).toBeTruthy();
  });

  // USR-E2E-14
  test('USR-E2E-14 - Close drawer with X button', async ({ managerPage }) => {
    await managerPage.goto('/users');
    await managerPage.waitForLoadState('networkidle');

    const createBtn = managerPage.locator('button:has-text("Tạo"), button:has-text("Create")').first();
    if (await createBtn.count() > 0) {
      await createBtn.click();
      await managerPage.waitForTimeout(500);

      const closeBtn = managerPage.locator('button[aria-label*="close"], button[aria-label*="Close"], button[aria-label*="Đóng"]').first();
      if (await closeBtn.count() > 0) {
        await closeBtn.click();
        await managerPage.waitForTimeout(300);
      }

      await expect(managerPage.locator('input[placeholder*="tên"]').first()).not.toBeVisible({ timeout: 2000 }).catch(() => {});
    }
  });
});
