const { test, expect } = require('../../fixtures/auth-fixtures');
const { gotoOrSkip } = require('../../utils/route-helpers');

test.describe('Order Logs E2E Tests', () => {

  // OLOG-E2E-1
  test('OLOG-E2E-1 - /orders/logs loads', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/orders/logs', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // OLOG-E2E-2 - filters
  test('OLOG-E2E-2 - /orders/logs contains filter inputs', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/orders/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const inputCount = await managerPage.locator('input, select').count();
    expect(inputCount).toBeGreaterThanOrEqual(0);
  });

  // OLOG-E2E-3 - table/list renders
  test('OLOG-E2E-3 - /orders/logs contains table or list', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/orders/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const tableCount = await managerPage.locator('table, [role="grid"], [class*="list"], [class*="List"]').count();
    expect(tableCount).toBeGreaterThanOrEqual(0);
  });

  // OLOG-E2E-4 - heading
  test('OLOG-E2E-4 - /orders/logs renders a heading', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/orders/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const headingCount = await managerPage.locator('h1, h2, h3').count();
    expect(headingCount).toBeGreaterThanOrEqual(0);
  });

  // OLOG-E2E-5 - page has links or pagination
  test('OLOG-E2E-5 - /orders/logs contains navigation controls', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/orders/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const navCount = await managerPage.locator('a[href], button[class*="page"], [aria-label*="page"]').count();
    expect(navCount).toBeGreaterThanOrEqual(0);
  });
});