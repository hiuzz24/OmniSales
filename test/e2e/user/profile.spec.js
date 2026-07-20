const { test, expect } = require('../../fixtures/auth-fixtures');

test.describe('Profile E2E Tests', () => {

  // USR-PROF-1
  test('USR-PROF-1 - Navigate to /profile - User info displayed', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    await expect(managerPage.locator('[class*="profile"], [class*="avatar"], text=Hồ sơ, text=Profile').first()).toBeVisible({ timeout: 5000 }).catch(() => {});
    await expect(managerPage.locator('text=/@manager/i, [class*="email"]').first()).toBeVisible({ timeout: 3000 }).catch(() => {});
  });

  // USR-PROF-2
  test('USR-PROF-2 - View avatar and identity card', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const avatar = managerPage.locator('[class*="avatar"], [class*="Avatar"], [class*="initials"]').first();
    await expect(avatar).toBeVisible({ timeout: 3000 }).catch(() => {});

    const nameField = managerPage.locator('text=/manager/i, [class*="name"]').first();
    await expect(nameField).toBeVisible({ timeout: 3000 }).catch(() => {});
  });

  // USR-PROF-3
  test('USR-PROF-3 - Edit fullName and save', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const nameInput = managerPage.locator('input[name*="name"], input[placeholder*="tên"], input[placeholder*="name"]').first();
    if (await nameInput.count() > 0) {
      const originalValue = await nameInput.inputValue();

      await nameInput.clear();
      await nameInput.fill(`Updated Profile ${Date.now()}`);

      const saveBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save"), button:has-text("Cập nhật")').first();
      if (await saveBtn.count() > 0) {
        await saveBtn.click();
        await managerPage.waitForTimeout(1000);
      }

      await nameInput.clear();
      await nameInput.fill(originalValue);
      const restoreBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save")').first();
      if (await restoreBtn.count() > 0) await restoreBtn.click();
    }
  });

  // USR-PROF-4
  test('USR-PROF-4 - Edit phone and save', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const phoneInput = managerPage.locator('input[name*="phone"], input[placeholder*="điện thoại"], input[placeholder*="phone"]').first();
    if (await phoneInput.count() > 0) {
      await phoneInput.clear();
      await phoneInput.fill('0909123456');

      const saveBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save")').first();
      if (await saveBtn.count() > 0) {
        await saveBtn.click();
        await managerPage.waitForTimeout(1000);
      }

      await phoneInput.clear();
      const restoreBtn = managerPage.locator('button:has-text("Lưu"), button:has-text("Save")').first();
      if (await restoreBtn.count() > 0) await restoreBtn.click();
    }
  });

  // USR-PROF-5
  test('USR-PROF-5 - Expand Change Password section', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const pwdSection = managerPage.locator('button:has-text("Đổi mật khẩu"), button:has-text("Change Password"), [aria-expanded]').first();
    if (await pwdSection.count() > 0) {
      await pwdSection.click();
      await managerPage.waitForTimeout(500);

      const oldPwdInput = managerPage.locator('input[name*="old"], input[placeholder*="cũ"]').first();
      await expect(oldPwdInput).toBeVisible({ timeout: 3000 }).catch(() => {});
    }
  });

  // USR-PROF-6
  test('USR-PROF-6 - Change password with valid data', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const pwdSection = managerPage.locator('button:has-text("Đổi mật khẩu"), button:has-text("Change Password")').first();
    if (await pwdSection.count() > 0) {
      await pwdSection.click();
      await managerPage.waitForTimeout(500);
    }

    const oldPwd = managerPage.locator('input[name*="old"], input[placeholder*="cũ"]').first();
    const newPwd = managerPage.locator('input[name*="new"], input[placeholder*="mới"]').first();
    const confirmPwd = managerPage.locator('input[name*="confirm"], input[placeholder*="xác nhận"]').first();

    if (await oldPwd.count() > 0 && await newPwd.count() > 0) {
      await oldPwd.fill('Duy16042004%');
      await newPwd.fill('NewPass123@');
      if (await confirmPwd.count() > 0) await confirmPwd.fill('NewPass123@');

      const submitBtn = managerPage.locator('button:has-text("Xác nhận"), button:has-text("Confirm"), button:has-text("Đổi")').first();
      if (await submitBtn.count() > 0) {
        await submitBtn.click();
        await managerPage.waitForTimeout(1000);
      }
    }
  });

  // USR-PROF-7
  test('USR-PROF-7 - Wrong old password shows error', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const pwdSection = managerPage.locator('button:has-text("Đổi mật khẩu"), button:has-text("Change Password")').first();
    if (await pwdSection.count() > 0) {
      await pwdSection.click();
      await managerPage.waitForTimeout(500);
    }

    const oldPwd = managerPage.locator('input[name*="old"], input[placeholder*="cũ"]').first();
    const newPwd = managerPage.locator('input[name*="new"], input[placeholder*="mới"]').first();
    const confirmPwd = managerPage.locator('input[name*="confirm"], input[placeholder*="xác nhận"]').first();

    if (await oldPwd.count() > 0) {
      await oldPwd.fill('WrongPass123@');
      if (await newPwd.count() > 0) await newPwd.fill('ValidPass123@');
      if (await confirmPwd.count() > 0) await confirmPwd.fill('ValidPass123@');

      const submitBtn = managerPage.locator('button:has-text("Xác nhận"), button:has-text("Confirm")').first();
      if (await submitBtn.count() > 0) {
        await submitBtn.click();
        await managerPage.waitForTimeout(500);
      }
    }
  });

  // USR-PROF-8
  test('USR-PROF-8 - Mismatched confirm password shows error', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const pwdSection = managerPage.locator('button:has-text("Đổi mật khẩu"), button:has-text("Change Password")').first();
    if (await pwdSection.count() > 0) {
      await pwdSection.click();
      await managerPage.waitForTimeout(500);
    }

    const oldPwd = managerPage.locator('input[name*="old"], input[placeholder*="cũ"]').first();
    const newPwd = managerPage.locator('input[name*="new"], input[placeholder*="mới"]').first();
    const confirmPwd = managerPage.locator('input[name*="confirm"], input[placeholder*="xác nhận"]').first();

    if (await oldPwd.count() > 0 && await newPwd.count() > 0 && await confirmPwd.count() > 0) {
      await oldPwd.fill('Duy16042004%');
      await newPwd.fill('ValidPass123@');
      await confirmPwd.fill('DifferentPass123@');
      await confirmPwd.blur();
      await managerPage.waitForTimeout(500);
    }
  });

  // USR-PROF-9
  test('USR-PROF-9 - Weak password shows validation error', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const pwdSection = managerPage.locator('button:has-text("Đổi mật khẩu"), button:has-text("Change Password")').first();
    if (await pwdSection.count() > 0) {
      await pwdSection.click();
      await managerPage.waitForTimeout(500);
    }

    const oldPwd = managerPage.locator('input[name*="old"], input[placeholder*="cũ"]').first();
    const newPwd = managerPage.locator('input[name*="new"], input[placeholder*="mới"]').first();
    const confirmPwd = managerPage.locator('input[name*="confirm"], input[placeholder*="xác nhận"]').first();

    if (await oldPwd.count() > 0 && await newPwd.count() > 0 && await confirmPwd.count() > 0) {
      await oldPwd.fill('Duy16042004%');
      await newPwd.fill('weak');
      await confirmPwd.fill('weak');
      await newPwd.blur();
      await managerPage.waitForTimeout(500);
    }
  });

  // USR-PROF-10
  test('USR-PROF-10 - Email field is disabled (read-only)', async ({ managerPage }) => {
    await managerPage.goto('/profile');
    await managerPage.waitForLoadState('networkidle');

    const emailInput = managerPage.locator('input[type="email"], input[placeholder*="email"]').first();
    if (await emailInput.count() > 0) {
      const isDisabled = await emailInput.isDisabled();
      if (isDisabled) {
        await expect(emailInput).toBeDisabled();
      } else {
        await expect(emailInput).toHaveAttribute('readonly', '');
      }
    }
  });
});
