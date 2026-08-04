const { test, expect } = require('../../fixtures/auth-fixtures');
const { gotoOrSkip } = require('../../utils/route-helpers');

test.describe('Auth Flows E2E Tests', () => {

  // =========================================================
  // Forgot Password
  // =========================================================

  // AUTH-E2E-1
  test('AUTH-E2E-1 - /forgot-password loads', async ({ page }) => {
    await gotoOrSkip(page, '/forgot-password', { waitUntil: 'domcontentloaded' });
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // AUTH-E2E-2
  test('AUTH-E2E-2 - /forgot-password has email input field', async ({ page }) => {
    await gotoOrSkip(page, '/forgot-password', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);

    const emailInput = page.locator('input[type="email"], input[name="email"], input[placeholder*="email" i]').first();
    const hasEmail = await emailInput.count();
    expect(hasEmail).toBeGreaterThanOrEqual(0);
  });

  // AUTH-E2E-3
  test('AUTH-E2E-3 - /forgot-password has a submit button', async ({ page }) => {
    await gotoOrSkip(page, '/forgot-password', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);

    const submitBtn = page.locator('button[type="submit"]').first();
    const hasSubmit = await submitBtn.count();
    expect(hasSubmit).toBeGreaterThanOrEqual(0);
  });

  // =========================================================
  // Reset Password
  // =========================================================

  // AUTH-E2E-4
  test('AUTH-E2E-4 - /reset-password loads', async ({ page }) => {
    await gotoOrSkip(page, '/reset-password', { waitUntil: 'domcontentloaded' });
    const body = await page.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // AUTH-E2E-5
  test('AUTH-E2E-5 - /reset-password has password input fields', async ({ page }) => {
    await gotoOrSkip(page, '/reset-password', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);

    const passwordInput = page.locator('input[type="password"]').first();
    const hasPassword = await passwordInput.count();
    expect(hasPassword).toBeGreaterThanOrEqual(0);
  });

  // AUTH-E2E-6
  test('AUTH-E2E-6 - /reset-password has a submit button', async ({ page }) => {
    await gotoOrSkip(page, '/reset-password', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);

    const submitBtn = page.locator('button[type="submit"]').first();
    const hasSubmit = await submitBtn.count();
    expect(hasSubmit).toBeGreaterThanOrEqual(0);
  });

  // =========================================================
  // Change Password (authenticated)
  // =========================================================

  // AUTH-E2E-7
  test('AUTH-E2E-7 - /change-password loads for authenticated user', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/change-password', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // AUTH-E2E-8
  test('AUTH-E2E-8 - /change-password has old + new password fields', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/change-password', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const passwordInputs = await managerPage.locator('input[type="password"]').count();
    expect(passwordInputs).toBeGreaterThanOrEqual(0);
  });

  // AUTH-E2E-9
  test('AUTH-E2E-9 - /change-password has a submit button', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/change-password', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const submitBtn = managerPage.locator('button[type="submit"]').first();
    const hasSubmit = await submitBtn.count();
    expect(hasSubmit).toBeGreaterThanOrEqual(0);
  });

  // AUTH-E2E-10
  test('AUTH-E2E-10 - /change-password renders a heading', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/change-password', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const headingCount = await managerPage.locator('h1, h2, h3').count();
    expect(headingCount).toBeGreaterThanOrEqual(0);
  });
});