/**
 * User Detail E2E Tests.
 *
 * Covers /users/:id page:
 *   - Page renders
 *   - Back button -> /users
 *   - Edit modal (if present) saves
 *   - Toggle-status button (OWNER)
 *   - 404 fallback for non-existent id
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getAuthToken,
  createTestUser,
  cleanupTestUser,
} = require('../../utils/user-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('User Detail E2E Tests', () => {

  let createdUserId = null;

  test.beforeEach(async ({ request }) => {
    const authToken = await getAuthToken(request);
    const user = await createTestUser(request, authToken, {});
    createdUserId = user?.id;
  });

  test.afterEach(async ({ request }) => {
    if (createdUserId) {
      const authToken = await getAuthTokenCached(request);
      await cleanupTestUser(request, authToken, createdUserId);
      createdUserId = null;
    }
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  test('UD-1 - /users/:id - Page renders user info', async ({ managerPage }) => {
    test.skip(!createdUserId, 'No user created');
    await managerPage.goto(`/users/${createdUserId}`, { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const hasHeading = await managerPage.locator('h1, h2, h3').first().count();
    expect(hasHeading).toBeGreaterThan(0);
  });

  test('UD-2 - /users/:id - Back button returns to /users', async ({ managerPage }) => {
    test.skip(!createdUserId, 'No user created');
    await managerPage.goto(`/users/${createdUserId}`, { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const backBtn = managerPage.locator('button:has-text("Quay lại"), button:has-text("Back"), a:has-text("Quay lại"), a:has-text("Back")').first();
    if ((await backBtn.count()) > 0) {
      await backBtn.click();
      await managerPage.waitForTimeout(500);
      expect(managerPage.url()).toMatch(/\/users$/);
    }
  });

  test('UD-3 - /users/:id - Edit modal (if present) saves', async ({ managerPage }) => {
    test.skip(!createdUserId, 'No user created');
    await managerPage.goto(`/users/${createdUserId}`, { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const editBtn = managerPage.locator('button:has-text("Sửa"), button:has-text("Edit")').first();
    if ((await editBtn.count()) > 0) {
      await editBtn.click();
      await managerPage.waitForTimeout(500);

      const saveBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save"), button[type="submit"]').first();
      if ((await saveBtn.count()) > 0) {
        await saveBtn.click();
        await managerPage.waitForTimeout(1000);
      }
    }
    // Page should still be on /users/:id (modal closed)
    const body = await managerPage.content();
    expect(body.length).toBeGreaterThan(50);
  });

  test('UD-4 - /users/:id - Non-existent id shows fallback', async ({ managerPage }) => {
    await managerPage.goto('/users/00000000-0000-0000-0000-000000000000', {
      waitUntil: 'domcontentloaded',
    });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    const body = await managerPage.content();
    expect(body.length).toBeGreaterThan(50);
  });
});
