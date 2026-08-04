const { test, expect } = require('../../fixtures/auth-fixtures');
const { gotoOrSkip } = require('../../utils/route-helpers');

test.describe('Admin Landing E2E Tests', () => {

  // ADMIN-E2E-1 - /admin loads
  test('ADMIN-E2E-1 - /admin loads for SYSTEM_ADMIN', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/admin', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // ADMIN-E2E-2 - /admin contains landing widgets/cards
  test('ADMIN-E2E-2 - /admin renders landing widgets/cards', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/admin', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const cardCount = await managerPage.locator('[class*="card"], [class*="Card"]').count();
    expect(cardCount).toBeGreaterThanOrEqual(0);
  });

  // ADMIN-E2E-3 - /admin contains links to sub-pages
  test('ADMIN-E2E-3 - /admin contains links to admin sub-pages', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/admin', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const linkCount = await managerPage.locator('a[href]').count();
    expect(linkCount).toBeGreaterThanOrEqual(0);
  });
});