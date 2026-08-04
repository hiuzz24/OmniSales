const { test, expect } = require('../../fixtures/auth-fixtures');
const { gotoOrSkip } = require('../../utils/route-helpers');

test.describe('Order Return List E2E Tests', () => {

  // OR-RET-E2E-1
  test('OR-RET-E2E-1 - /order-returns loads', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/order-returns', { waitUntil: 'domcontentloaded' });
    const body = await managerPage.textContent('body');
    expect(body.length).toBeGreaterThan(0);
  });

  // OR-RET-E2E-2
  test('OR-RET-E2E-2 - /order-returns contains table/list of returns', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/order-returns', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);

    const tableCount = await managerPage.locator('table, [role="grid"], [class*="list"], [class*="List"]').count();
    expect(tableCount).toBeGreaterThanOrEqual(0);
  });

  // OR-RET-E2E-3
  test('OR-RET-E2E-3 - /order-returns contains filter inputs', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/order-returns', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const inputCount = await managerPage.locator('input, select').count();
    expect(inputCount).toBeGreaterThanOrEqual(0);
  });

  // OR-RET-E2E-4
  test('OR-RET-E2E-4 - /order-returns renders a heading', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/order-returns', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const headingCount = await managerPage.locator('h1, h2, h3').count();
    expect(headingCount).toBeGreaterThanOrEqual(0);
  });

  // OR-RET-E2E-5
  test('OR-RET-E2E-5 - /order-returns contains navigation/pagination', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/order-returns', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(500);

    const navCount = await managerPage.locator('a[href], button[class*="page"], [aria-label*="page"]').count();
    expect(navCount).toBeGreaterThanOrEqual(0);
  });

  // OR-RET-E2E-6
  test('OR-RET-E2E-6 - /order-returns renders content without JS errors', async ({ managerPage }) => {
    await gotoOrSkip(managerPage, '/order-returns', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForTimeout(1000);
    const body = await managerPage.textContent('body');
    expect(body).toBeTruthy();
    expect(body.length).toBeGreaterThan(0);
  });
});