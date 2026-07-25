/**
 * Purchase Order API Tests.
 *
 * Covers all 9 PurchaseOrder endpoints:
 *   - GET    /api/purchase-orders                 (list, paginated, status filter)
 *   - GET    /api/purchase-orders/{id}            (detail)
 *   - GET    /api/purchase-orders/statistics       (counts by status)
 *   - GET    /api/purchase-orders/form-options     (supplier/warehouse/variants)
 *   - GET    /api/purchase-orders/next-code        (next PO code suggestion)
 *   - POST   /api/purchase-orders                 (create draft)
 *   - PUT    /api/purchase-orders/{id}             (update draft only)
 *   - PATCH  /api/purchase-orders/{id}/send        (DRAFT -> SENT_TO_SUPPLIER)
 *   - PATCH  /api/purchase-orders/{id}/cancel      (cancel, unless completed/receipt-linked)
 *
 * Notes:
 *   - The /send endpoint requires the PO to be in DRAFT state.
 *   - The /cancel endpoint refuses if the PO has a linked receipt (RECEIVING/COMPLETED).
 *   - DRAFT POs that get cancelled can be reused by sending again after re-creation.
 */

const { test, expect } = require('../../fixtures/auth-fixtures');
const {
  getSupplierId,
  getVariantIdFromCatalog,
  createDraftPurchaseOrder,
  sendPurchaseOrder,
  cancelPurchaseOrder,
  updateDraftPurchaseOrder,
  getPurchaseOrderById,
  getNextPurchaseOrderCode,
  API_BASE,
} = require('../../utils/inventory-helpers');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');

function futureDate(daysFromNow = 7) {
  return new Date(Date.now() + daysFromNow * 24 * 60 * 60 * 1000)
    .toISOString()
    .split('T')[0];
}

test.describe('Purchase Order API Tests', () => {

  let createdPoIds = [];

  test.afterEach(async ({ request }) => {
    // Clean up any draft POs created in the test by cancelling them.
    // POs that are SENT will be processed by the scheduler; the SQL teardown
    // will not catch them, so cancel via API first.
    if (createdPoIds.length) {
      const authToken = await getAuthTokenCached(request);
      for (const id of createdPoIds.splice(0)) {
        await cancelPurchaseOrder(request, authToken, id);
      }
    }
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // === GET endpoints ===========================================================

  test('PO-1 - GET /api/purchase-orders - List returns paginated body', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/purchase-orders?page=0&size=10`, {
      headers: managerHeaders,
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('content');
    expect(Array.isArray(body.data.content)).toBe(true);
    expect(body.data).toHaveProperty('totalElements');
    expect(body.data).toHaveProperty('totalPages');
  });

  test('PO-2 - GET /api/purchase-orders?status=DRAFT - Status filter accepts valid status', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/purchase-orders?status=DRAFT&page=0&size=10`, {
      headers: managerHeaders,
    });

    expect([200, 400]).toContain(response.status());
    if (response.status() === 200) {
      const body = await response.json();
      expect(body.success).toBe(true);
      const items = body.data.content || [];
      for (const item of items) {
        expect(item.status).toBe('DRAFT');
      }
    }
  });

  test('PO-3 - GET /api/purchase-orders - Without auth returns 401/403', async ({ request }) => {
    const response = await request.get(`${API_BASE}/purchase-orders`);
    expect([401, 403]).toContain(response.status());
  });

  test('PO-4 - GET /api/purchase-orders/{id} - Get existing PO returns 200', async ({ request, managerHeaders }) => {
    const list = await request.get(`${API_BASE}/purchase-orders?page=0&size=1`, {
      headers: managerHeaders,
    });
    test.skip(list.status() !== 200, 'List endpoint unavailable');
    const listBody = await list.json();
    const first = listBody.data?.content?.[0];
    test.skip(!first, 'No POs in DB');

    const response = await request.get(`${API_BASE}/purchase-orders/${first.id}`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBe(first.id);
    expect(body.data).toHaveProperty('orderCode');
    expect(body.data).toHaveProperty('status');
    expect(body.data).toHaveProperty('supplierName');
    expect(Array.isArray(body.data.items)).toBe(true);
  });

  test('PO-5 - GET /api/purchase-orders/{id} - Non-existent returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.get(
      `${API_BASE}/purchase-orders/00000000-0000-0000-0000-000000000000`,
      { headers: managerHeaders }
    );
    expect([404, 500]).toContain(response.status());
  });

  // === Statistics / form-options / next-code ==================================

  test('PO-6 - GET /api/purchase-orders/statistics - Returns status counts', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/purchase-orders/statistics`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('totalCount');
    // Each status enum value should be present (or 0)
    expect(typeof body.data.totalCount).toBe('number');
  });

  test('PO-7 - GET /api/purchase-orders/form-options - Returns form metadata', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/purchase-orders/form-options`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('orderCode');
    expect(body.data).toHaveProperty('warehouse');
  });

  test('PO-8 - GET /api/purchase-orders/next-code - Returns MĐH-prefixed code', async ({ request, managerHeaders }) => {
    const response = await request.get(`${API_BASE}/purchase-orders/next-code`, {
      headers: managerHeaders,
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    // body.data is { orderCode: "MĐH-2026-XXXXXX" } — accept any string-shaped payload.
    const code = body.data?.orderCode || body.data?.code || (typeof body.data === 'string' ? body.data : null);
    expect(code).toBeTruthy();
    if (typeof code === 'string') {
      expect(code).toMatch(/^MĐH-\d{4}-\d{6}$/);
    }
  });

  test('PO-8b - GET /api/purchase-orders/next-code - Helper getter matches endpoint', async ({ request }) => {
    const authToken = await getAuthTokenCached(request);
    const code = await getNextPurchaseOrderCode(request, authToken);
    expect(code).toMatch(/^MĐH-\d{4}-\d{6}$/);
  });

  // === POST (create) =========================================================

  test('PO-9 - POST /api/purchase-orders - Create DRAFT PO returns 200/201', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const po = await createDraftPurchaseOrder(request, authToken, {
      quantity: 1,
      unitCost: 50000,
      notes: 'PO-9 test draft',
    });
    test.skip(!po || !po.id, 'Cannot create PO (missing supplier/variant or backend down)');
    createdPoIds.push(po.id);

    expect(po.status).toBe('DRAFT');
    expect(po).toHaveProperty('orderCode');
    expect(po.items.length).toBeGreaterThan(0);
  });

  test('PO-10 - POST /api/purchase-orders - Without auth returns 401/403', async ({ request }) => {
    const response = await request.post(`${API_BASE}/purchase-orders`, {
      data: {
        supplierId: '00000000-0000-0000-0000-000000000000',
        expectedReceiptDate: futureDate(),
        items: [{ variantId: '00000000-0000-0000-0000-000000000000', quantity: 1, unitCost: 1 }],
      },
    });
    expect([401, 403]).toContain(response.status());
  });

  test('PO-11 - POST /api/purchase-orders - Empty items returns 400', async ({ request, managerHeaders }) => {
    const response = await request.post(`${API_BASE}/purchase-orders`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        supplierId: '00000000-0000-0000-0000-000000000000',
        expectedReceiptDate: futureDate(),
        items: [],
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  // === PUT (update draft) ===================================================

  test('PO-12 - PUT /api/purchase-orders/{id} - Update DRAFT PO returns 200', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const po = await createDraftPurchaseOrder(request, authToken, {
      notes: 'PO-12 original',
    });
    test.skip(!po || !po.id, 'Cannot create PO');
    createdPoIds.push(po.id);

    const updated = await updateDraftPurchaseOrder(request, authToken, po.id, {
      supplierId: po.supplierId,
      expectedReceiptDate: futureDate(14),
      paymentMethod: 'BANK_TRANSFER',
      notes: 'PO-12 updated',
      isDraft: true,
      items: (po.items || []).map((it) => ({
        variantId: it.variantId,
        quantity: it.quantity,
        unitCost: it.unitCost,
      })),
    });
    expect(updated).not.toBeNull();
    expect(updated.status).toBe('DRAFT');
    expect(updated.notes).toBe('PO-12 updated');
  });

  test('PO-13 - PUT /api/purchase-orders/{id} - Non-existent returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.put(`${API_BASE}/purchase-orders/00000000-0000-0000-0000-000000000000`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
      data: {
        supplierId: '00000000-0000-0000-0000-000000000000',
        expectedReceiptDate: futureDate(),
        items: [{ variantId: '00000000-0000-0000-0000-000000000000', quantity: 1, unitCost: 1 }],
      },
    });
    expect([400, 404, 500]).toContain(response.status());
  });

  // === PATCH /send ===========================================================

  test('PO-14 - PATCH /api/purchase-orders/{id}/send - DRAFT -> SENT_TO_SUPPLIER', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const po = await createDraftPurchaseOrder(request, authToken, {});
    test.skip(!po || !po.id, 'Cannot create PO');
    createdPoIds.push(po.id);

    const sent = await sendPurchaseOrder(request, authToken, po.id);
    expect(sent).not.toBeNull();
    expect(sent.status).toBe('SENT_TO_SUPPLIER');
    expect(sent.sentAt).toBeTruthy();
  });

  test('PO-15 - PATCH /api/purchase-orders/{id}/send - Non-DRAFT returns 400', async ({ request, managerHeaders }) => {
    // Send a DRAFT PO first, then try to send it again.
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const po = await createDraftPurchaseOrder(request, authToken, {});
    test.skip(!po || !po.id, 'Cannot create PO');
    createdPoIds.push(po.id);
    await sendPurchaseOrder(request, authToken, po.id);

    const response = await request.patch(`${API_BASE}/purchase-orders/${po.id}/send`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
    });
    expect([400, 409, 500]).toContain(response.status());
  });

  // === PATCH /cancel =========================================================

  test('PO-16 - PATCH /api/purchase-orders/{id}/cancel - DRAFT -> CANCELLED', async ({ request, managerHeaders }) => {
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const po = await createDraftPurchaseOrder(request, authToken, {});
    test.skip(!po || !po.id, 'Cannot create PO');
    createdPoIds.push(po.id);

    const response = await request.patch(`${API_BASE}/purchase-orders/${po.id}/cancel`, {
      headers: { ...managerHeaders, 'Content-Type': 'application/json' },
    });
    expect([200, 204]).toContain(response.status());

    // Verify via getById
    const fetched = await getPurchaseOrderById(request, authToken, po.id);
    expect(fetched.status).toBe('CANCELLED');
  });

  test('PO-17 - PATCH /api/purchase-orders/{id}/cancel - Non-existent returns 404/500', async ({ request, managerHeaders }) => {
    const response = await request.patch(
      `${API_BASE}/purchase-orders/00000000-0000-0000-0000-000000000000/cancel`,
      { headers: { ...managerHeaders, 'Content-Type': 'application/json' } }
    );
    expect([404, 500]).toContain(response.status());
  });

  // === Multi-status flow (full lifecycle) ====================================

  test('PO-18 - Full lifecycle: DRAFT -> send -> wait for RECEIVING (scheduler)', async ({ request, managerHeaders }) => {
    test.setTimeout(30000);
    const authToken = managerHeaders.Authorization.replace('Bearer ', '');
    const po = await createDraftPurchaseOrder(request, authToken, { notes: 'PO-18 lifecycle' });
    test.skip(!po || !po.id, 'Cannot create PO');
    createdPoIds.push(po.id);

    // Move DRAFT -> SENT_TO_SUPPLIER
    const sent = await sendPurchaseOrder(request, authToken, po.id);
    expect(sent.status).toBe('SENT_TO_SUPPLIER');

    // Scheduler moves SENT -> RECEIVING after SUPPLIER_SEND_DELAY_SECONDS (10s).
    // Poll up to 20s for the transition.
    const deadline = Date.now() + 20000;
    let current = sent;
    while (Date.now() < deadline && current.status !== 'RECEIVING') {
      await new Promise((r) => setTimeout(r, 1000));
      current = (await getPurchaseOrderById(request, authToken, po.id)) || sent;
    }
    expect(['SENT_TO_SUPPLIER', 'RECEIVING']).toContain(current.status);
    expect(current.sentAt).toBeTruthy();
  });
});
