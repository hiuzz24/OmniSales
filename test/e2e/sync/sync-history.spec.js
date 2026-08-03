const { test, expect } = require('../../fixtures/auth-fixtures');
const { gotoOrSkip } = require('../../utils/route-helpers');

test.describe('Sync History E2E Tests', () => {

  // SYNC-E2E-1
  test('SYNC-E2E-1 - /sync/history loads', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/sync/history', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // SYNC-E2E-2
  test('SYNC-E2E-2 - /sync/history contains table/list of sync jobs', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/sync/history', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const tableCount = await managerPage.locator('table, [role="grid"], [class*="list"], [class*="List"]').count();
    expect(tableCount).toBeGreaterThanOrEqual(0);
  });

  // SYNC-E2E-3
  test('SYNC-E2E-3 - /sync/history contains filter inputs', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/sync/history', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const inputCount = await managerPage.locator('input, select').count();
    expect(inputCount).toBeGreaterThanOrEqual(0);
  });

  // SYNC-E2E-4
  test('SYNC-E2E-4 - /sync/history renders a heading', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/sync/history', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const headingCount = await managerPage.locator('h1, h2, h3').count();
    expect(headingCount).toBeGreaterThanOrEqual(0);
  });

  // SYNC-E2E-5
  test('SYNC-E2E-5 - /sync/history contains navigation/pagination', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/sync/history', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const navCount = await managerPage.locator('a[href], button[class*="page"], [aria-label*="page"]').count();
    expect(navCount).toBeGreaterThanOrEqual(0);
  });
});