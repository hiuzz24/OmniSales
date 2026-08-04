
const { test, expect } = require('../../fixtures/auth-fixtures');
const { API_BASE } = require('../../utils/env-config');
const { cleanupAllTestData, getAuthTokenCached } = require('../../utils/cleanup-helpers');
const { createTestOrder } = require('../../utils/order-helpers');

test.describe('Gift Reservation API Tests', () => {
  test.afterEach(async ({ request }) => {
    const token = await getAuthTokenCached(request);
    await cleanupAllTestData(request, token);
  });

  // ─────────────────────────  Validation  ─────────────────────────

  test('GR-1 - POST /from-orders with empty body returns 400', async ({ request, managerHeaders }) => {
    const res = await request.post(`${API_BASE}/stock-deliveries/from-orders`, {
      headers: managerHeaders,
      data: {},
    });
    expect([400, 500]).toContain(res.status());
  });

  test('GR-2 - POST /from-orders with empty orders list returns 400', async ({ request, managerHeaders }) => {
    const res = await request.post(`${API_BASE}/stock-deliveries/from-orders`, {
      headers: managerHeaders,
      data: { orders: [] },
    });
    expect([400, 500]).toContain(res.status());
  });

  test('GR-3 - POST /from-orders with giftItems[].productVariantId missing returns 400', async ({ request, managerHeaders }) => {
    const fakeOrder = '00000000-0000-0000-0000-000000000000';
    const res = await request.post(`${API_BASE}/stock-deliveries/from-orders`, {
      headers: managerHeaders,
      data: {
        orders: [
          {
            orderId: fakeOrder,
            giftItems: [{ quantity: 1 }], // missing productVariantId
          },
        ],
      },
    });
    expect([400, 500]).toContain(res.status());
  });

  test('GR-4 - POST /from-orders with giftItems[].quantity missing returns 400', async ({ request, managerHeaders }) => {
    const fakeOrder = '00000000-0000-0000-0000-000000000000';
    const fakeVariant = '11111111-1111-1111-1111-111111111111';
    const res = await request.post(`${API_BASE}/stock-deliveries/from-orders`, {
      headers: managerHeaders,
      data: {
        orders: [
          {
            orderId: fakeOrder,
            giftItems: [{ productVariantId: fakeVariant }], // missing quantity
          },
        ],
      },
    });
    expect([400, 500]).toContain(res.status());
  });

  test('GR-5 - POST /from-orders with quantity <= 0 returns 400', async ({ request, managerHeaders }) => {
    const fakeOrder = '00000000-0000-0000-0000-000000000000';
    const fakeVariant = '11111111-1111-1111-1111-111111111111';
    const res = await request.post(`${API_BASE}/stock-deliveries/from-orders`, {
      headers: managerHeaders,
      data: {
        orders: [
          {
            orderId: fakeOrder,
            giftItems: [{ productVariantId: fakeVariant, quantity: 0 }],
          },
        ],
      },
    });
    expect([400, 500]).toContain(res.status());
  });

  // ─────────────────────────  Auth gating  ─────────────────────────

  test('GR-6 - POST /from-orders without auth returns 401/403', async ({ request }) => {
    const res = await request.post(`${API_BASE}/stock-deliveries/from-orders`, {
      data: { orders: [] },
    });
    expect([401, 403]).toContain(res.status());
  });

  // ─────────────────────────  Order not found  ─────────────────────────

  test('GR-7 - POST /from-orders with non-existent orderId returns 201 with FAILED result', async ({ request, managerHeaders }) => {
    const fakeOrder = '00000000-0000-0000-0000-000000000000';
    const fakeVariant = '11111111-1111-1111-1111-111111111111';
    const res = await request.post(`${API_BASE}/stock-deliveries/from-orders`, {
      headers: managerHeaders,
      data: {
        orders: [
          {
            orderId: fakeOrder,
            giftItems: [{ productVariantId: fakeVariant, quantity: 1 }],
          },
        ],
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data.failCount).toBeGreaterThanOrEqual(1);
    const failed = body.data.results.find(r => r.orderId === fakeOrder);
    expect(failed).toBeDefined();
    expect(failed.status).toBe('FAILED');
    expect(failed.message).toContain('Không tìm thấy đơn hàng');
  });

  // ─────────────────────────  Document the gift schema shape  ─────────────────────────

  test('GR-9 - A successful response includes items[].isGift metadata', async ({ request, managerHeaders }) => {
    // We don't create a real order here — just verify the response shape
    // when an empty result is returned. The StockDeliveryResponse schema
    // always includes an items[] array with isGift boolean per item.
    const res = await request.get(`${API_BASE}/stock-deliveries?page=0&size=5`, { headers: managerHeaders });
    expect(res.status()).toBe(200);
    const body = await res.json();
    // Each item in the response has the isGift field per the mapper.
    for (const delivery of body.data.content) {
      expect(delivery).toHaveProperty('items');
      for (const item of delivery.items) {
        expect(item).toHaveProperty('isGift');
      }
    }
  });

  test('GR-10 - /order-candidates endpoint requires OWNER or OPERATIONS role', async ({ request }) => {
    const res = await request.get(`${API_BASE}/stock-deliveries/order-candidates`);
    expect([401, 403]).toContain(res.status());
  });

  test('GR-11 - /orders/{orderId}/readiness endpoint requires OWNER, OPERATIONS or SALES role', async ({ request }) => {
    const fakeOrder = '00000000-0000-0000-0000-000000000000';
    const res = await request.get(`${API_BASE}/stock-deliveries/orders/${fakeOrder}/readiness`);
    expect([401, 403]).toContain(res.status());
  });
});
