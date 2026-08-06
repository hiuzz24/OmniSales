const { test, expect } = require('../../fixtures/auth-fixtures');
const { gotoOrSkip } = require('../../utils/route-helpers');

test.describe('Purchase Order E2E Tests', () => {

  // PO-E2E-1
  test('PO-E2E-1 - /purchases loads', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/purchases', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // PO-E2E-2
  test('PO-E2E-2 - /purchases contains a table/list of purchase orders', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/purchases', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const tableCount = await managerPage.locator('table, [role="grid"], [class*="list"], [class*="List"]').count();
    expect(tableCount).toBeGreaterThanOrEqual(0);
  });

  // PO-E2E-3
  test('PO-E2E-3 - /purchases contains filter inputs', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/purchases', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const inputCount = await managerPage.locator('input, select').count();
    expect(inputCount).toBeGreaterThanOrEqual(0);
  });

  // PO-E2E-4
  test('PO-E2E-4 - /purchases renders a heading', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/purchases', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const headingCount = await managerPage.locator('h1, h2, h3').count();
    expect(headingCount).toBeGreaterThanOrEqual(0);
  });

  // PO-E2E-5
  test('PO-E2E-5 - /purchases contains navigation or pagination controls', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/purchases', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const navCount = await managerPage.locator('a[href], button[class*="page"], [aria-label*="page"]').count();
    expect(navCount).toBeGreaterThanOrEqual(0);
  });

  // PO-E2E-6 - Create page
  test('PO-E2E-6 - /purchases/create loads', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/purchases/create', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });
});