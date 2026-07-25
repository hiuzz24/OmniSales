/**
 * Helper utilities for inventory API and E2E tests
 * Re-exports warehouse helpers plus inventory-specific helpers.
 */

const {
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE: ENV_API_BASE,
} = require('./env-config');
const API_BASE = process.env.API_BASE || ENV_API_BASE;

const warehouseHelpers = require('./warehouse-helpers');

const {
  loginAsOwner,
  loginAsOperations,
  getAuthToken,
  getAuthHeaders,
  getWarehouses,
  getWarehouseId,
  getSuppliers,
  getSupplierId,
  getVariants,
  getVariantId,
  getAvailableVariants,
  getVariantsFromCatalog,
  getVariantIdFromCatalog,
  createTestReceipt,
  createTestDelivery,
  createTestStocktake,
  createTestTransfer,
  addInventory,
  uniqueCode,
  cleanupTestData,
} = warehouseHelpers;

/**
 * Record an inventory transaction manually (used for transaction spec).
 * InvTxnType values: INBOUND, OUTBOUND, ADJUSTMENT, TRANSFER_OUT, TRANSFER_IN,
 *                     RESERVATION, RELEASE, STOCKTAKE_DELTA
 */
async function recordTransaction(request, token, overrides = {}) {
  const warehouseId = overrides.warehouseId || (await getWarehouseId(request, token));
  let variantId = overrides.variantId;
  if (!variantId && warehouseId) {
    variantId = await getVariantId(request, token, warehouseId);
  }

  const data = {
    warehouseId,
    variantId,
    type: overrides.type || 'ADJUSTMENT',
    referenceType: overrides.referenceType || null,
    referenceId: overrides.referenceId || null,
    quantityChange: overrides.quantityChange ?? 1,
    note: overrides.note || `Test transaction ${Date.now()}`,
  };

  const response = await request.post(`${API_BASE}/inventory/transactions`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data,
  });
  if (response.status() === 200 || response.status() === 201) {
    const body = await response.json();
    return body.data || body;
  }
  return null;
}

/**
 * Get low-stock items list (used in inventory spec).
 */
async function getLowStockItems(request, token) {
  const response = await request.get(`${API_BASE}/inventory/items/low-stock`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (response.status() === 200) {
    const body = await response.json();
    return body.data || body || [];
  }
  return [];
}

/**
 * Get inventory transactions (variant filter supported).
 */
async function getInventoryTransactions(request, token, variantId) {
  const url = variantId
    ? `${API_BASE}/inventory/detail/transactions?variantId=${variantId}&page=0&size=20`
    : `${API_BASE}/inventory/detail/transactions?page=0&size=20`;
  const response = await request.get(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (response.status() === 200) {
    const body = await response.json();
    return body.data || body || {};
  }
  return {};
}

/**
 * Get variants belonging to a warehouse via /inventory/warehouses/{warehouseId}/items
 * Returns the variantId of the first item, or null.
 */
async function getFirstWarehouseVariantId(request, token, warehouseId) {
  if (!warehouseId) return null;
  const response = await request.get(`${API_BASE}/inventory/warehouses/${warehouseId}/items`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (response.status() === 200) {
    const body = await response.json();
    const data = body.data || body;
    if (data && Array.isArray(data.content)) {
      return data.content[0]?.variantId || data.content[0]?.id || null;
    }
    if (Array.isArray(data) && data.length > 0) {
      return data[0].variantId || data[0].id || null;
    }
  }
  return null;
}

/**
 * Create a test category (used in product-variant/category specs).
 * Cleans up at end via DELETE /api/categories/{id}.
 */
async function createTestCategory(request, token, overrides = {}) {
  const timestamp = Date.now();
  const body = {
    name: overrides.name || `Test Category ${timestamp}`,
    slug: overrides.slug || `test-category-${timestamp}-${Math.floor(Math.random() * 9999)}`,
    sortOrder: overrides.sortOrder ?? 0,
    parentId: overrides.parentId || null,
    status: overrides.status || 'ACTIVE',
  };
  const response = await request.post(`${API_BASE}/categories`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: body,
  });
  if (response.status() === 200 || response.status() === 201) {
    const j = await response.json();
    return j.data || j;
  }
  return null;
}

/**
 * Delete a category by id (best effort).
 */
async function deleteTestCategory(request, token, categoryId) {
  if (!categoryId) return;
  try {
    await request.delete(`${API_BASE}/categories/${categoryId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (e) {
    // ignore
  }
}

/**
 * Generate a unique SKU for test isolation.
 * Mirrors product-helpers uniqueSku signature.
 */
function uniqueSku(prefix = 'TEST') {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 99999)}`;
}

/**
 * Create a Purchase Order in the 'SENT_TO_SUPPLIER' state and wait for the
 * backend scheduler to flip it to 'RECEIVING' (the only state accepted by
 * the receipt create/update endpoints).
 *
 * The scheduler runs roughly every second and moves a PO from
 * SENT_TO_SUPPLIER to RECEIVING once its `sentAt` is older than
 * `SUPPLIER_SEND_DELAY_SECONDS` (10s). We poll the PO every ~500ms for up
 * to `maxWaitMs` (default 15s) before giving up.
 *
 * @returns {Promise<{id:string,orderCode:string,supplierId:string,variantId:string,quantity:number}|null>}
 *          The PO with id + first item on success, or null on failure.
 */
  async function createReceivingPurchaseOrder(
    request,
    token,
    { supplierId, variantId, quantity = 10, unitCost = 50000, maxWaitMs = 15000 } = {}
  ) {
    if (!variantId) {
      variantId = await getVariantIdFromCatalog(request, token)
        .then(v => (v && (v.id || v.variantId)) || null)
        .catch(() => null);
    }
    if (!supplierId) {
      supplierId = await getSupplierId(request, token);
    }
    if (!supplierId) {
      supplierId = await createTestSupplier(request, token);
    }
    if (!variantId || !supplierId) {
      return null;
    }

    const expectedReceiptDate = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
      .toISOString()
      .split('T')[0];

    const createResp = await request.post(`${API_BASE}/purchase-orders`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: {
        supplierId,
        expectedReceiptDate,
        isDraft: false,
        items: [{ variantId, quantity, unitCost }],
      },
    });

    if (createResp.status() !== 200 && createResp.status() !== 201) {
      return null;
    }

    const created = (await createResp.json()).data;
    const orderId = created && created.id;
    if (!orderId) return null;

    const deadline = Date.now() + maxWaitMs;
    while (Date.now() < deadline) {
      const poll = await request.get(`${API_BASE}/purchase-orders/${orderId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (poll.status() === 200) {
        const data = (await poll.json()).data;
        if (data && data.status === 'RECEIVING') {
          return {
            id: orderId,
            orderCode: data.orderCode,
            supplierId: data.supplierId,
            variantId,
            quantity,
          };
        }
      }
      await new Promise(r => setTimeout(r, 500));
    }

    // Still SENT_TO_SUPPLIER after timeout — return anyway so the caller can
    // decide what to do. The receipt endpoints will reject it with a clear
    // error rather than masking the root cause.
    return {
      id: orderId,
      orderCode: created.orderCode,
      supplierId,
      variantId,
      quantity,
      status: created.status,
    };
  }

  /**
   * Create a fresh, active supplier. Caller can set it as inactive via
   * `PATCH /api/suppliers/{id}/status` afterwards for cleanup.
   */
  async function createTestSupplier(request, token) {
    const ts = Date.now();
    const body = {
      name: `TestSup${ts}-${Math.floor(Math.random() * 9999)}`,
      contactName: 'Test Contact',
      email: `supplier${ts}-${Math.floor(Math.random() * 9999)}@example.com`,
      phone: '0987654321',
      address: 'Test Address',
      isActive: true,
    };
    try {
      const resp = await request.post(`${API_BASE}/suppliers`, {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        data: body,
      });
      if (resp.status() === 200 || resp.status() === 201) {
        const j = await resp.json();
        return (j.data && j.data.id) || null;
      }
    } catch (_) {
      // ignore
    }
    return null;
  }

/**
 * Cancel a previously created purchase order. Best-effort.
 */
async function cancelPurchaseOrder(request, token, orderId) {
  if (!orderId) return;
  try {
    await request.patch(`${API_BASE}/purchase-orders/${orderId}/cancel`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });
  } catch (_) {
    // ignore
  }
}

/**
 * Send a DRAFT purchase order to the supplier (PATCH /api/purchase-orders/{id}/send).
 * Returns the updated PO response, or null on failure.
 */
async function sendPurchaseOrder(request, token, orderId) {
  if (!orderId) return null;
  try {
    const resp = await request.patch(`${API_BASE}/purchase-orders/${orderId}/send`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });
    if (resp.status() === 200) {
      return (await resp.json()).data;
    }
  } catch (_) {
    // ignore
  }
  return null;
}

/**
 * Update (replace) a DRAFT purchase order via PUT /api/purchase-orders/{id}.
 * Returns the updated PO response, or null on failure.
 */
async function updateDraftPurchaseOrder(request, token, orderId, payload) {
  if (!orderId) return null;
  try {
    const resp = await request.put(`${API_BASE}/purchase-orders/${orderId}`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: payload,
    });
    if (resp.status() === 200) {
      return (await resp.json()).data;
    }
  } catch (_) {
    // ignore
  }
  return null;
}

/**
 * Get a purchase order by id (GET /api/purchase-orders/{id}).
 * Returns the PO response, or null on failure.
 */
async function getPurchaseOrderById(request, token, orderId) {
  if (!orderId) return null;
  try {
    const resp = await request.get(`${API_BASE}/purchase-orders/${orderId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (resp.status() === 200) {
      return (await resp.json()).data;
    }
  } catch (_) {
    // ignore
  }
  return null;
}

/**
 * Create a DRAFT purchase order (isDraft:true). Used by API tests that
 * exercise send/cancel/update endpoints which require a non-RECEIVING PO.
 *
 * @returns {Promise<object|null>} created PO data, or null on failure
 */
async function createDraftPurchaseOrder(request, token, overrides = {}) {
  const supplierId = overrides.supplierId || (await getSupplierId(request, token));
  let variantId = overrides.variantId;
  if (!variantId) {
    variantId = await getVariantIdFromCatalog(request, token)
      .then((v) => (v && (v.variantId || v.id)) || null)
      .catch(() => null);
  }
  if (!supplierId || !variantId) {
    if (process.env.DEBUG) {
      console.log('[createDraftPurchaseOrder] missing supplier/variant', { supplierId, variantId });
    }
    return null;
  }

  const expectedReceiptDate = overrides.expectedReceiptDate ||
    new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

  const payload = {
    supplierId,
    expectedReceiptDate,
    paymentMethod: overrides.paymentMethod || 'CASH',
    notes: overrides.notes || 'Test draft PO',
    isDraft: true,
    items: overrides.items || [
      {
        variantId,
        quantity: overrides.quantity || 1,
        unitCost: overrides.unitCost || 50000,
      },
    ],
  };

  try {
    const resp = await request.post(`${API_BASE}/purchase-orders`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: payload,
    });
    if (resp.status() === 200 || resp.status() === 201) {
      return (await resp.json()).data;
    }
  } catch (_) {
    // ignore
  }
  return null;
}

/**
 * Read the next PO code suggestion (GET /api/purchase-orders/next-code).
 * Returns the suggested code string, or null on failure.
 */
async function getNextPurchaseOrderCode(request, token) {
  try {
    const resp = await request.get(`${API_BASE}/purchase-orders/next-code`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (resp.status() === 200) {
      const body = await resp.json();
      return (body.data && (body.data.orderCode || body.data.code)) || null;
    }
  } catch (_) {
    // ignore
  }
  return null;
}

/**
 * Generate a unique slug for category tests.
 */
function uniqueSlug(prefix = 'cat') {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 99999)}`;
}

module.exports = {
  // auth
  loginAsOwner,
  loginAsOperations,
  getAuthToken,
  getAuthHeaders,
  // warehouses/suppliers
  getWarehouses,
  getWarehouseId,
  getSuppliers,
  getSupplierId,
  // variants
  getVariants,
  getVariantId,
  getAvailableVariants,
  getVariantsFromCatalog,
  getVariantIdFromCatalog,
  getFirstWarehouseVariantId,
  // create operations
  createTestReceipt,
  createTestDelivery,
  createTestStocktake,
  createTestTransfer,
  createTestCategory,
  createReceivingPurchaseOrder,
  createDraftPurchaseOrder,
  sendPurchaseOrder,
  updateDraftPurchaseOrder,
  getPurchaseOrderById,
  getNextPurchaseOrderCode,
  cancelPurchaseOrder,
  createTestSupplier,
  // utility
  addInventory,
  recordTransaction,
  getLowStockItems,
  getInventoryTransactions,
  deleteTestCategory,
  uniqueCode,
  uniqueSku,
  uniqueSlug,
  cleanupTestData,
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
};
