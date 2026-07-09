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
