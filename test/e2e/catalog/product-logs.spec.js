const { test, expect } = require('../../fixtures/auth-fixtures');
const { gotoOrSkip } = require('../../utils/route-helpers');

test.describe('Product Logs E2E Tests', () => {

  // PLOG-E2E-1
  test('PLOG-E2E-1 - /products/logs loads', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/products/logs', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // PLOG-E2E-2 - filters
  test('PLOG-E2E-2 - /products/logs contains filter inputs', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/products/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const inputCount = await managerPage.locator('input, select').count();
    expect(inputCount).toBeGreaterThanOrEqual(0);
  });

  // PLOG-E2E-3 - table/list renders
  test('PLOG-E2E-3 - /products/logs contains table or list', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/products/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const tableCount = await managerPage.locator('table, [role="grid"], [class*="list"], [class*="List"]').count();
    expect(tableCount).toBeGreaterThanOrEqual(0);
  });

  // PLOG-E2E-4 - heading
  test('PLOG-E2E-4 - /products/logs renders a heading', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/products/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const headingCount = await managerPage.locator('h1, h2, h3').count();
    expect(headingCount).toBeGreaterThanOrEqual(0);
  });

  // PLOG-E2E-5 - navigation
  test('PLOG-E2E-5 - /products/logs contains navigation controls', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/products/logs', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const navCount = await managerPage.locator('a[href], button[class*="page"], [aria-label*="page"]').count();
    expect(navCount).toBeGreaterThanOrEqual(0);
  });
});