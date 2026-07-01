/**
 * Helper utilities for warehouse/inventory E2E and API tests
 * Uses the same credentials as auth.spec.js and product-helpers.js
 */

const API_BASE = process.env.API_BASE || 'http://localhost:8080/api';
const TEST_EMAIL = 'manager@osms.vn';
const TEST_PASSWORD = 'Duy16042004%';
const { expect } = require('@playwright/test');

/**
 * Login as owner via UI (for E2E tests)
 */
async function loginAsOwner(page) {
  await page.goto('/login');
  await page.locator('#login-email').fill(TEST_EMAIL);
  await page.locator('#login-password').fill(TEST_PASSWORD);

  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 8000 }),
    page.locator('#login-submit-btn').click(),
  ]);

  await expect(page).toHaveURL(/\/dashboard/);
}

/**
 * Login as operations staff via UI (for E2E tests)
 * Uses the same credentials as owner since we only have manager@osms.vn
 */
async function loginAsOperations(page) {
  await page.goto('/login');
  await page.locator('#login-email').fill(TEST_EMAIL);
  await page.locator('#login-password').fill(TEST_PASSWORD);

  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 8000 }),
    page.locator('#login-submit-btn').click(),
  ]);

  await expect(page).toHaveURL(/\/dashboard/);
}

/**
 * Login via API and return access token
 */
async function getAuthToken(request) {
  const response = await request.post(`${API_BASE}/auth/login`, {
    data: { email: TEST_EMAIL, password: TEST_PASSWORD },
  });

  if (response.status() !== 200) {
    throw new Error(`Login failed with status ${response.status()}`);
  }

  const body = await response.json();
  return body.data.accessToken;
}

/**
 * Get a valid auth header for API calls
 */
async function getAuthHeaders(request) {
  const token = await getAuthToken(request);
  return { Authorization: `Bearer ${token}` };
}

/**
 * Get list of warehouses
 */
async function getWarehouses(request, token) {
  const response = await request.get(`${API_BASE}/warehouses`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() === 200) {
    const body = await response.json();
    return body.data || body;
  }
  return [];
}

/**
 * Get first warehouse ID
 */
async function getWarehouseId(request, token) {
  const warehouses = await getWarehouses(request, token);
  if (warehouses.length > 0) {
    return warehouses[0].id;
  }
  return null;
}

/**
 * Get list of suppliers
 */
async function getSuppliers(request, token) {
  const response = await request.get(`${API_BASE}/suppliers`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() === 200) {
    const body = await response.json();
    return body.data || body;
  }
  return [];
}

/**
 * Get first supplier ID
 */
async function getSupplierId(request, token) {
  const suppliers = await getSuppliers(request, token);
  if (suppliers.length > 0) {
    return suppliers[0].id;
  }
  return null;
}

/**
 * Get variants from a warehouse for inventory
 */
async function getVariants(request, token, warehouseId) {
  const response = await request.get(`${API_BASE}/inventory/warehouses/${warehouseId}/items`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() === 200) {
    const body = await response.json();
    return body.data || body;
  }
  return [];
}

/**
 * Get a variant ID from warehouse inventory
 */
async function getVariantId(request, token, warehouseId) {
  const variants = await getVariants(request, token, warehouseId);
  if (variants.length > 0) {
    return variants[0].variantId || variants[0].id;
  }
  return null;
}

/**
 * Get available variants for stock transfer
 */
async function getAvailableVariants(request, token, warehouseId) {
  const response = await request.get(`${API_BASE}/transfer/available-variants?warehouseId=${warehouseId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() === 200) {
    const body = await response.json();
    return body.data || body;
  }
  return [];
}

/**
 * Create a test stock receive receipt
 */
async function createTestReceipt(request, token, overrides = {}) {
  // Get warehouse ID if not provided
  let warehouseId = overrides.warehouseId;
  if (!warehouseId) {
    warehouseId = await getWarehouseId(request, token);
  }

  // Get supplier ID if not provided
  let supplierId = overrides.supplierId;
  if (!supplierId) {
    supplierId = await getSupplierId(request, token);
  }

  // Get variant ID if not provided
  let variantId = overrides.variantId;
  if (!variantId && warehouseId) {
    variantId = await getVariantId(request, token, warehouseId);
  }

  const timestamp = Date.now();

  const defaultReceipt = {
    warehouseId: warehouseId,
    supplierId: supplierId,
    receivedAt: new Date().toISOString().split('T')[0],
    isDraft: false,
    items: variantId ? [
      {
        variantId: variantId,
        quantity: 10,
        unitCost: 50000,
        notes: `Test item ${timestamp}`,
      },
    ] : [],
  };

  const receiptData = { ...defaultReceipt, ...overrides };

  const response = await request.post(`${API_BASE}/receipts`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: receiptData,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    const errorBody = await response.text();
    throw new Error(`Failed to create test receipt: ${response.status()} - ${errorBody}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Create a test stock delivery
 */
async function createTestDelivery(request, token, overrides = {}) {
  let warehouseId = overrides.warehouseId;
  if (!warehouseId) {
    warehouseId = await getWarehouseId(request, token);
  }

  // Get variant with inventory
  let variantId = overrides.variantId;
  let quantity = overrides.quantity || 5;
  if (!variantId && warehouseId) {
    const variants = await getVariants(request, token, warehouseId);
    if (variants.length > 0) {
      variantId = variants[0].variantId || variants[0].id;
    }
  }

  const timestamp = Date.now();

  const defaultDelivery = {
    warehouseId: warehouseId,
    deliveryType: overrides.deliveryType || 'ORDER',
    issuedDate: new Date().toISOString().split('T')[0],
    items: variantId ? [
      {
        productVariantId: variantId,
        quantity: quantity,
        note: `Test delivery ${timestamp}`,
      },
    ] : [],
  };

  const deliveryData = { ...defaultDelivery, ...overrides };

  const response = await request.post(`${API_BASE}/stock-deliveries`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: deliveryData,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    const errorBody = await response.text();
    throw new Error(`Failed to create test delivery: ${response.status()} - ${errorBody}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Create a test stocktake session
 */
async function createTestStocktake(request, token, overrides = {}) {
  let warehouseId = overrides.warehouseId;
  if (!warehouseId) {
    warehouseId = await getWarehouseId(request, token);
  }

  // Get variants from warehouse
  let variants = overrides.variants;
  if (!variants && warehouseId) {
    const variantItems = await getVariants(request, token, warehouseId);
    if (variantItems.length > 0) {
      variants = variantItems.slice(0, 3).map(item => ({
        variantId: item.variantId || item.id,
        systemQuantity: item.quantityOnHand || 10,
        actualQuantity: item.quantityOnHand || 10,
      }));
    }
  }

  const timestamp = Date.now();

  const defaultStocktake = {
    warehouseId: warehouseId,
    sessionCode: `KK-${Date.now()}`,
    scheduledDate: new Date().toISOString().split('T')[0],
    items: variants || [],
  };

  const stocktakeData = { ...defaultStocktake, ...overrides };

  const response = await request.post(`${API_BASE}/stocktakes`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: stocktakeData,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    const errorBody = await response.text();
    throw new Error(`Failed to create test stocktake: ${response.status()} - ${errorBody}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Create a test stock transfer
 */
async function createTestTransfer(request, token, overrides = {}) {
  const warehouses = await getWarehouses(request, token);
  
  if (warehouses.length < 2) {
    throw new Error('Need at least 2 warehouses for stock transfer test');
  }

  const fromWarehouseId = overrides.fromWarehouseId || warehouses[0].id;
  const toWarehouseId = overrides.toWarehouseId || warehouses[1].id;

  // Get available variants from source warehouse
  let variants = overrides.variants;
  if (!variants && fromWarehouseId) {
    const availableVariants = await getAvailableVariants(request, token, fromWarehouseId);
    if (availableVariants.length > 0) {
      const variant = availableVariants[0];
      const maxQty = variant.availableQuantity || 10;
      variants = [{
        variantId: variant.variantId || variant.id,
        quantity: Math.min(maxQty, 5),
      }];
    }
  }

  const timestamp = Date.now();

  const defaultTransfer = {
    fromWarehouseId: fromWarehouseId,
    toWarehouseId: toWarehouseId,
    transferCode: `CK-${Date.now()}`,
    transferDate: new Date().toISOString().split('T')[0],
    status: 'DRAFT',
    items: variants || [],
  };

  const transferData = { ...defaultTransfer, ...overrides };

  const response = await request.post(`${API_BASE}/transfer`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: transferData,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    const errorBody = await response.text();
    throw new Error(`Failed to create test transfer: ${response.status()} - ${errorBody}`);
  }

  const body = await response.json();
  return body.data;
}

/**
 * Generate a unique code for test isolation
 */
function uniqueCode(prefix = 'TEST') {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 99999)}`;
}

/**
 * Cleanup test data - delete created receipts, deliveries, etc.
 */
async function cleanupTestData(request, token, type, id) {
  if (!id) return;

  try {
    switch (type) {
      case 'receipt':
        // Try to delete or cancel the receipt
        await request.delete(`${API_BASE}/receipts/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        }).catch(() => {});
        break;
      case 'delivery':
        // Try to cancel the delivery first
        await request.put(`${API_BASE}/stock-deliveries/${id}/cancel`, {
          headers: { Authorization: `Bearer ${token}` },
        }).catch(() => {});
        break;
      case 'stocktake':
        // Try to cancel the stocktake
        await request.put(`${API_BASE}/stocktakes/${id}/status`, {
          headers: { Authorization: `Bearer ${token}` },
          data: { status: 'CANCELLED' },
        }).catch(() => {});
        break;
      case 'transfer':
        // Try to cancel the transfer
        await request.patch(`${API_BASE}/transfer/${id}/status`, {
          headers: { Authorization: `Bearer ${token}` },
          data: { status: 'CANCELLED' },
        }).catch(() => {});
        break;
    }
  } catch (e) {
    // Ignore cleanup errors
  }
}

/**
 * Add inventory to a warehouse (create a receipt and complete it)
 */
async function addInventory(request, token, warehouseId, variantId, quantity, unitCost = 50000) {
  const receipt = await createTestReceipt(request, token, {
    warehouseId,
    variantId,
    items: [{
      variantId,
      quantity,
      unitCost,
      notes: 'Setup for tests',
    }],
  });

  // Complete the receipt if it's a draft
  if (receipt.status === 'DRAFT') {
    await request.patch(`${API_BASE}/receipts/${receipt.id}/complete`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  }

  return receipt;
}

module.exports = {
  // Login helpers
  loginAsOwner,
  loginAsOperations,
  getAuthToken,
  getAuthHeaders,

  // Data seeding helpers
  getWarehouses,
  getWarehouseId,
  getSuppliers,
  getSupplierId,
  getVariants,
  getVariantId,
  getAvailableVariants,

  // Test data creation
  createTestReceipt,
  createTestDelivery,
  createTestStocktake,
  createTestTransfer,
  addInventory,

  // Utilities
  uniqueCode,
  cleanupTestData,

  // Constants
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
};
