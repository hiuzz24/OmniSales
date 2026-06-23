const { test, expect } = require('@playwright/test');

test.describe('Auth E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('Login successfully and redirects to dashboard', async ({ page }) => {
    await page.locator('#login-email').fill('manager@osms.vn');
    await page.locator('#login-password').fill('Duy16042004%');

    await Promise.all([
      page.waitForURL('**/dashboard', { timeout: 8000 }),
      page.locator('#login-submit-btn').click(),
    ]);

    await expect(page).toHaveURL(/\/dashboard/);

    const toast = page.locator('.Toastify__toast').first();
    await expect(toast).toBeVisible({ timeout: 5000 });
  });

  test('Login page renders correctly', async ({ page }) => {
    await expect(page.locator('#login-email')).toBeVisible();
    await expect(page.locator('#login-password')).toBeVisible();
    await expect(page.locator('#login-submit-btn')).toBeVisible();
  });

  test('Shows validation error when email is empty', async ({ page }) => {
    await page.locator('#login-submit-btn').click();

    await expect(page.locator('#email-error')).toBeVisible();
    const emailError = await page.locator('#email-error').textContent();
    expect(emailError).toContain('Vui lòng nhập địa chỉ email');
  });

  test('Shows validation error when password is empty', async ({ page }) => {
    await page.locator('#login-email').fill('test@example.com');
    await page.locator('#login-submit-btn').click();

    await expect(page.locator('#password-error')).toBeVisible();
  });

  test('Shows validation error for invalid email format', async ({ page }) => {
    await page.locator('#login-email').fill('not-an-email');
    await page.locator('#login-submit-btn').click();

    await expect(page.locator('#email-error')).toBeVisible();
    const emailError = await page.locator('#email-error').textContent();
    expect(emailError).toContain('email');
  });

  test('Shows error banner for invalid credentials', async ({ page }) => {
    await page.locator('#login-email').fill('wrong@example.com');
    await page.locator('#login-password').fill('WrongPassword123@');
    await page.locator('#login-submit-btn').click();

    await expect(page.locator('div[role="alert"]').first()).toBeVisible({ timeout: 5000 });
  });

  test('Toggle password visibility', async ({ page }) => {
    const passwordInput = page.locator('#login-password');

    await expect(passwordInput).toHaveAttribute('type', 'password');

    await page.locator('button[aria-label="Hiển thị mật khẩu"]').click();
    await expect(passwordInput).toHaveAttribute('type', 'text');

    await page.locator('button[aria-label="Ẩn mật khẩu"]').click();
    await expect(passwordInput).toHaveAttribute('type', 'password');
  });

  test('Remember me checkbox works', async ({ page }) => {
    const checkbox = page.locator('input[type="checkbox"]');
    await checkbox.check();
    await expect(checkbox).toBeChecked();

    await checkbox.uncheck();
    await expect(checkbox).not.toBeChecked();
  });

  test('Forgot password link navigates correctly', async ({ page }) => {
    const forgotLink = page.locator('a:has-text("Quên mật khẩu")');
    await expect(forgotLink).toHaveAttribute('href', '/forgot-password');
  });
});
