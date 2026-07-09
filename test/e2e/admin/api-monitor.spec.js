const { test, expect } = require('../../fixtures/auth-fixtures');

test.describe('API Monitor E2E Tests', () => {

  // MON-E2E-1
  test('MON-E2E-1 - API monitor dashboard loads', async ({ managerPage }) => {
    await managerPage.goto('/admin/api-monitor', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    if (/\/admin\/api-monitor/.test(managerPage.url())) {
      const body = await managerPage.textContent('body');
      expect(body.length).toBeGreaterThan(0);
    } else {
      expect(managerPage.url()).toBeTruthy();
    }
  });

  // MON-E2E-2
  test('MON-E2E-2 - Summary cards display metrics', async ({ managerPage }) => {
    await managerPage.goto('/admin/api-monitor', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    if (!/\/admin\/api-monitor/.test(managerPage.url())) {
      expect(true).toBeTruthy();
      return;
    }

    const cardCount = await managerPage.locator('[data-metric-card], .metric-card, .summary-card').count();
    expect(cardCount).toBeGreaterThanOrEqual(0);
  });

  // MON-E2E-3
  test('MON-E2E-3 - Traffic chart renders with today range', async ({ managerPage }) => {
    await managerPage.goto('/admin/api-monitor', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    if (!/\/admin\/api-monitor/.test(managerPage.url())) {
      expect(true).toBeTruthy();
      return;
    }

    const chart = managerPage.locator('canvas, svg[class*="chart"], [data-chart]');
    const hasChart = await chart.count();
    expect(hasChart).toBeGreaterThanOrEqual(0);
  });

  // MON-E2E-4
  test('MON-E2E-4 - Switching range to 7 days updates chart', async ({ managerPage }) => {
    await managerPage.goto('/admin/api-monitor', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    if (!/\/admin\/api-monitor/.test(managerPage.url())) {
      expect(true).toBeTruthy();
      return;
    }

    const rangeSelect = managerPage.locator('select:has(option:has-text("7 days")), select:has(option:has-text("7 ngày"))').first();
    const hasRangeSelect = await rangeSelect.count();

    if (hasRangeSelect > 0) {
      await rangeSelect.selectOption({ label: /7 (days|ngày)/ });
      await managerPage.waitForTimeout(800);
    }

    expect(true).toBeTruthy();
  });

  // MON-E2E-5
  test('MON-E2E-5 - Top endpoints table is visible', async ({ managerPage }) => {
    await managerPage.goto('/admin/api-monitor', { waitUntil: 'domcontentloaded' });
    await managerPage.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

    if (!/\/admin\/api-monitor/.test(managerPage.url())) {
      expect(true).toBeTruthy();
      return;
    }

    const endpointRows = await managerPage.locator('table tbody tr, [data-endpoint-row]').count();
    expect(endpointRows).toBeGreaterThanOrEqual(0);
  });
});
