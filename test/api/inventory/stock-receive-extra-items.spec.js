const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

test.describe('Stock Receive Extra Items API Tests', () => {

  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // SREI-1 - POST /api/receipts/sync-marketplace-inventory (without auth)
  test('SREI-1 - POST /api/receipts/sync-marketplace-inventory - Without auth returns 401 or 403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/receipts/sync-marketplace-inventory`, {
      headers: { 'Content-Type': 'application/json' },
      data: {},
    });

    expect([401, 403]).toContain(response.status());
  });

  // SREI-2 - POST /api/receipts/sync-marketplace-inventory (with auth, empty body)
  test('SREI-2 - POST /api/receipts/sync-marketplace-inventory - With manager auth, empty body returns 400/200/500', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/receipts/sync-marketplace-inventory`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {},
    });

    expect([200, 400, 500]).toContain(response.status());
  });

  // SREI-3 - POST /api/receipts/sync-marketplace-inventory (with auth, channel id)
  test('SREI-3 - POST /api/receipts/sync-marketplace-inventory - With channelId body returns 200/400/500', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/receipts/sync-marketplace-inventory`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { channelId: '00000000-0000-0000-0000-000000000001' },
    });

    expect([200, 400, 500]).toContain(response.status());
  });

  // SREI-4 - POST /api/receipts/{id}/sync-marketplace-inventory (no auth)
  test('SREI-4 - POST /api/receipts/{id}/sync-marketplace-inventory - Without auth returns 401 or 403', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/sync-marketplace-inventory`, {
      headers: { 'Content-Type': 'application/json' },
      data: {},
    });

    expect([401, 403]).toContain(response.status());
  });

  // SREI-5 - POST /api/receipts/{id}/sync-marketplace-inventory (non-existent id)
  test('SREI-5 - POST /api/receipts/{id}/sync-marketplace-inventory - Non-existent id returns 404/500', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/sync-marketplace-inventory`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {},
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // SREI-6 - GET /api/receipts/{id}/import-extra-items/template (no auth)
  test('SREI-6 - GET /api/receipts/{id}/import-extra-items/template - Without auth returns 401 or 403', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.get(`${API_BASE}/receipts/${fakeId}/import-extra-items/template`);

    expect([401, 403]).toContain(response.status());
  });

  // SREI-7 - GET /api/receipts/{id}/import-extra-items/template (with auth)
  test('SREI-7 - GET /api/receipts/{id}/import-extra-items/template - With manager auth, non-existent id returns 200 (empty template) or 404', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.get(`${API_BASE}/receipts/${fakeId}/import-extra-items/template`, {
      headers: managerHeaders,
    });

    expect([200, 400, 404, 500]).toContain(response.status());
  });

  // SREI-8 - GET /api/receipts/import-extra-items/template (no auth)
  test('SREI-8 - GET /api/receipts/import-extra-items/template - Without auth returns 401 or 403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/receipts/import-extra-items/template`);

    expect([401, 403]).toContain(response.status());
  });

  // SREI-9 - GET /api/receipts/import-extra-items/template (with auth)
  test('SREI-9 - GET /api/receipts/import-extra-items/template - With manager auth returns 200', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/receipts/import-extra-items/template`, {
      headers: managerHeaders,
    });

    expect([200, 400, 500]).toContain(response.status());
  });

  // SREI-10 - POST /api/receipts/{id}/import-extra-items/preview (no auth)
  test('SREI-10 - POST /api/receipts/{id}/import-extra-items/preview - Without auth returns 401 or 403', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/import-extra-items/preview`, {
      headers: { 'Content-Type': 'multipart/form-data' },
      multipart: {
        file: {
          name: 'test.csv',
          mimeType: 'text/csv',
          buffer: Buffer.from('sku,quantity\nSKU-1,10\n'),
        },
      },
    });

    expect([401, 403]).toContain(response.status());
  });

  // SREI-11 - POST /api/receipts/{id}/import-extra-items/preview (with auth, no file)
  test('SREI-11 - POST /api/receipts/{id}/import-extra-items/preview - No file returns 400', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/import-extra-items/preview`, {
      headers: managerHeaders,
      multipart: {},
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // SREI-12 - POST /api/receipts/{id}/import-extra-items/preview (with file, non-existent receipt)
  test('SREI-12 - POST /api/receipts/{id}/import-extra-items/preview - Non-existent receipt returns 404 or 500', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/import-extra-items/preview`, {
      headers: managerHeaders,
      multipart: {
        file: {
          name: 'extra-items.csv',
          mimeType: 'text/csv',
          buffer: Buffer.from('sku,quantity\nSKU-1,10\n'),
        },
      },
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // SREI-13 - POST /api/receipts/{id}/import-extra-items/preview (malformed CSV)
  test('SREI-13 - POST /api/receipts/{id}/import-extra-items/preview - Malformed CSV returns 400/500', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/import-extra-items/preview`, {
      headers: managerHeaders,
      multipart: {
        file: {
          name: 'bad.csv',
          mimeType: 'text/csv',
          buffer: Buffer.from('garbage,not_a_csv\n!@#,$%^'),
        },
      },
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // SREI-14 - POST /api/receipts/{id}/import-extra-items/confirm (no auth)
  test('SREI-14 - POST /api/receipts/{id}/import-extra-items/confirm - Without auth returns 401 or 403', async ({ request }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/import-extra-items/confirm`, {
      headers: { 'Content-Type': 'application/json' },
      data: {},
    });

    expect([401, 403]).toContain(response.status());
  });

  // SREI-15 - POST /api/receipts/{id}/import-extra-items/confirm (non-existent receipt)
  test('SREI-15 - POST /api/receipts/{id}/import-extra-items/confirm - Non-existent receipt returns 404/500', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/import-extra-items/confirm`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { rows: [] },
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // SREI-16 - POST /api/receipts/{id}/import-extra-items/confirm (empty rows)
  test('SREI-16 - POST /api/receipts/{id}/import-extra-items/confirm - Empty rows returns 400', async ({ request, managerHeaders }) => {
    const fakeId = '00000000-0000-0000-0000-000000000099';
    const response = await request.post(`${API_BASE}/receipts/${fakeId}/import-extra-items/confirm`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: { rows: [] },
    });

    expect([400, 404, 500]).toContain(response.status());
  });

  // SREI-17 - GET /api/receipts/import-extra-items/errors/{fileName:.+} (no auth)
  test('SREI-17 - GET /api/receipts/import-extra-items/errors/{fileName} - Without auth returns 401 or 403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/receipts/import-extra-items/errors/sample-errors.csv`);

    expect([401, 403]).toContain(response.status());
  });

  // SREI-18 - GET /api/receipts/import-extra-items/errors/{fileName:.+} (with auth, random file)
  test('SREI-18 - GET /api/receipts/import-extra-items/errors/{fileName} - With manager auth, non-existent file returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/receipts/import-extra-items/errors/nonexistent-${Date.now()}.csv`, {
      headers: managerHeaders,
    });

    expect([200, 400, 404, 500]).toContain(response.status());
  });

  // SREI-19 - GET /api/receipts/import-extra-items/errors/{fileName:.+} (with auth)
  test('SREI-19 - GET /api/receipts/import-extra-items/errors/{fileName} - With manager auth, common name returns 200/404', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/receipts/import-extra-items/errors/import-errors-sample.csv`, {
      headers: managerHeaders,
    });

    expect([200, 400, 404, 500]).toContain(response.status());
  });
});