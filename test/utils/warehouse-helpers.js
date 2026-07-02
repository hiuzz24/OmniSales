const API_BASE = process.env.API_BASE || 'http://localhost:8080/api';
const TEST_EMAIL = 'manager@osms.vn';
const TEST_PASSWORD = 'Duy16042004%';
const { expect } = require('@playwright/test');

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

async function getAuthHeaders(request) {
  const token = await getAuthToken(request);
  return { Authorization: `Bearer ${token}` };
}

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

async function getWarehouseId(request, token) {
  const warehouses = await getWarehouses(request, token);
  if (warehouses.length > 0) {
    return warehouses[0].id;
  }
  return null;
}

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

async function getSupplierId(request, token) {
  const suppliers = await getSuppliers(request, token);
  if (suppliers.length > 0) {
    return suppliers[0].id;
  }
  return null;
}

async function getVariantsFromCatalog(request, token) {
  const endpoints = [
    `${API_BASE}/catalog/variants?page=0&size=10`,
    `${API_BASE}/variants?page=0&size=10`,
    `${API_BASE}/products?page=0&size=10`,
  ];

  for (const url of endpoints) {
    try {
      const response = await request.get(url, {
        headers: { Authorization: `Bearer ${token}` },
      });

      if (response.status() === 200) {
        const body = await response.json();
        let data = body.data || body;

        if (data.content) {
          data = data.content;
        }

        if (Array.isArray(data) && data.length > 0) {
          return data.map(item => ({
            id: item.id || item.variantId,
            variantId: item.id || item.variantId,
            sku: item.sku,
            name: item.name || item.productName,
          }));
        }
      }
    } catch (e) {
    }
  }

  return [];
}

async function getVariantIdFromCatalog(request, token) {
  const variants = await getVariantsFromCatalog(request, token);
  if (variants.length > 0) {
    return variants[0];
  }
  return null;
}

async function getVariants(request, token, warehouseId) {
  if (!warehouseId) return [];

  const response = await request.get(`${API_BASE}/inventory/warehouses/${warehouseId}/items`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() === 200) {
    const body = await response.json();
    let data = body.data || body;

    if (data.content) {
      data = data.content;
    }

    return data;
  }
  return [];
}

async function getVariantId(request, token, warehouseId) {
  const variants = await getVariants(request, token, warehouseId);
  if (variants.length > 0) {
    return variants[0].variantId || variants[0].id;
  }

  return await getVariantIdFromCatalog(request, token);
}

async function getAvailableVariants(request, token, warehouseId) {
  if (!warehouseId) return [];

  const response = await request.get(`${API_BASE}/transfer/available-variants?warehouseId=${warehouseId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status() === 200) {
    const body = await response.json();
    return body.data || body;
  }
  return [];
}

async function createTestProduct(request, token) {
  const catResponse = await request.get(`${API_BASE}/categories?page=0&size=1`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  let categoryId = null;
  if (catResponse.status() === 200) {
    const catBody = await catResponse.json();
    const categories = catBody.data?.content || catBody.data || [];
    if (categories.length > 0) {
      categoryId = categories[0].id;
    }
  }

  if (!categoryId) {
    return null;
  }

  const productData = {
    name: `Test Product ${Date.now()}`,
    categoryId: categoryId,
    status: 'ACTIVE',
    variants: [
      {
        sku: `SKU-TEST-${Date.now()}`,
        price: 50000,
        costPrice: 30000,
        quantity: 100,
      },
    ],
  };

  const response = await request.post(`${API_BASE}/products`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: productData,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    return null;
  }

  const body = await response.json();
  return body.data;
}

async function setupInventory(request, token) {
  const product = await createTestProduct(request, token);
  if (!product || !product.id) {
    return false;
  }

  const warehouseId = await getWarehouseId(request, token);
  const supplierId = await getSupplierId(request, token);

  if (!warehouseId || !supplierId) {
    return false;
  }

  const variantId = product.variants?.[0]?.id;
  if (!variantId) {
    return false;
  }

  const receiptResponse = await request.post(`${API_BASE}/receipts`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: {
      warehouseId: warehouseId,
      supplierId: supplierId,
      receivedAt: new Date().toISOString().split('T')[0],
      isDraft: false,
      items: [{
        variantId: variantId,
        quantity: 100,
        unitCost: 30000,
        notes: 'Setup inventory for tests',
      }],
    },
  });

  return receiptResponse.status() === 200 || receiptResponse.status() === 201;
}

async function createTestReceipt(request, token, overrides = {}) {
  let warehouseId = overrides.warehouseId;
  if (!warehouseId) {
    warehouseId = await getWarehouseId(request, token);
  }

  let supplierId = overrides.supplierId;
  if (!supplierId) {
    supplierId = await getSupplierId(request, token);
  }

  let variantId = overrides.variantId;
  if (!variantId && warehouseId) {
    variantId = await getVariantId(request, token, warehouseId);
  }

  if (!variantId) {
    await setupInventory(request, token);
    variantId = await getVariantId(request, token, warehouseId);
  }

  if (!variantId) {
    const catalogVariants = await getVariantsFromCatalog(request, token);
    if (catalogVariants.length > 0) {
      variantId = catalogVariants[0].id || catalogVariants[0].variantId;
    }
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
    return null;
  }

  const body = await response.json();
  return body.data;
}

async function createTestDelivery(request, token, overrides = {}) {
  let warehouseId = overrides.warehouseId;
  if (!warehouseId) {
    warehouseId = await getWarehouseId(request, token);
  }

  let variantId = overrides.variantId;
  let quantity = overrides.quantity || 5;
  if (!variantId && warehouseId) {
    const variants = await getVariants(request, token, warehouseId);
    if (variants.length > 0) {
      variantId = variants[0].variantId || variants[0].id;
    } else {
      variantId = await getVariantIdFromCatalog(request, token);
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
    return null;
  }

  const body = await response.json();
  return body.data;
}

async function createTestStocktake(request, token, overrides = {}) {
  let warehouseId = overrides.warehouseId;
  if (!warehouseId) {
    warehouseId = await getWarehouseId(request, token);
  }

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

  if (!variants || variants.length === 0) {
    const catalogVariantIds = await getVariantsFromCatalog(request, token);
    if (catalogVariantIds.length > 0) {
      variants = catalogVariantIds.slice(0, 3).map(vId => ({
        variantId: vId,
        systemQuantity: 10,
        actualQuantity: 10,
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
    return null;
  }

  const body = await response.json();
  return body.data;
}

async function createTestTransfer(request, token, overrides = {}) {
  const warehouses = await getWarehouses(request, token);

  if (warehouses.length < 2) {
    return null;
  }

  const fromWarehouseId = overrides.fromWarehouseId || warehouses[0].id;
  const toWarehouseId = overrides.toWarehouseId || warehouses[1].id;

  let variants = overrides.variants;
  if (!variants && fromWarehouseId) {
    const availableVariants = await getAvailableVariants(request, token, fromWarehouseId);
    if (availableVariants.length > 0) {
      const variant = availableVariants[0];
      const maxQty = variant.availableQuantity || 10;
      variants = [{
        variantId: variant.variantId || variant.id,
        quantity: Math.min(maxQty, 5),
        unitCost: 50000,
      }];
    }
  }

  if (!variants || variants.length === 0) {
    const catalogVariantIds = await getVariantsFromCatalog(request, token);
    if (catalogVariantIds.length > 0) {
      variants = catalogVariantIds.slice(0, 3).map(vId => ({
        variantId: vId,
        quantity: 5,
        unitCost: 50000,
      }));
    }
  }

  if (!variants || variants.length === 0) {
    const warehouseIdToUse = warehouseId || await getWarehouseId(request, token);
    const supplierId = await getSupplierId(request, token);
    const catalogVariants = await getVariantsFromCatalog(request, token);

    if (warehouseIdToUse && supplierId && catalogVariants.length > 0) {
      const variantId = catalogVariants[0].id || catalogVariants[0].variantId;

      const receiptResponse = await request.post(`${API_BASE}/receipts`, {
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: {
          warehouseId: warehouseIdToUse,
          supplierId: supplierId,
          receivedAt: new Date().toISOString().split('T')[0],
          isDraft: false,
          items: [{
            variantId: variantId,
            quantity: 100,
            unitCost: 50000,
            notes: 'Setup inventory for tests',
          }],
        },
      });

      if (receiptResponse.status() === 200 || receiptResponse.status() === 201) {
        const warehouseVariants = await getVariants(request, token, warehouseIdToUse);
        if (warehouseVariants.length > 0) {
          variants = warehouseVariants.slice(0, 3).map(item => ({
            variantId: item.variantId || item.id,
            quantity: Math.min(item.quantityOnHand || 10, 5),
            unitCost: 50000,
          }));
        }
      }
    }
  }

  const timestamp = Date.now();

  const defaultTransfer = {
    fromWarehouseId: fromWarehouseId,
    toWarehouseId: toWarehouseId,
    transferCode: `CK-${Date.now()}`,
    transferDate: new Date().toISOString().split('T')[0],
    transferTime: new Date().toISOString(),
    status: 'DRAFT',
    items: variants || [],
  };

  const transferData = { ...defaultTransfer, ...overrides };

  const response = await request.post(`${API_BASE}/transfer`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: transferData,
  });

  if (response.status() !== 200 && response.status() !== 201) {
    return null;
  }

  const body = await response.json();
  return body.data;
}

function uniqueCode(prefix = 'TEST') {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 99999)}`;
}

async function cleanupTestData(request, token, type, id) {
  if (!id) return;

  try {
    switch (type) {
      case 'receipt':
        await request.delete(`${API_BASE}/receipts/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        }).catch(() => {});
        break;
      case 'delivery':
        await request.put(`${API_BASE}/stock-deliveries/${id}/cancel`, {
          headers: { Authorization: `Bearer ${token}` },
        }).catch(() => {});
        break;
      case 'stocktake':
        await request.put(`${API_BASE}/stocktakes/${id}/status`, {
          headers: { Authorization: `Bearer ${token}` },
          data: { status: 'CANCELLED' },
        }).catch(() => {});
        break;
      case 'transfer':
        await request.patch(`${API_BASE}/transfer/${id}/status`, {
          headers: { Authorization: `Bearer ${token}` },
          data: { status: 'CANCELLED' },
        }).catch(() => {});
        break;
    }
  } catch (e) {
  }
}

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

  if (receipt && receipt.status === 'DRAFT') {
    await request.patch(`${API_BASE}/receipts/${receipt.id}/complete`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  }

  return receipt;
}

module.exports = {
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
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
};
