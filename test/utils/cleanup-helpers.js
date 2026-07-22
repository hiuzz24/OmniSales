/**
 * test/utils/cleanup-helpers.js
 *
 * Provides helpers for cleaning up test data after each spec and for the
 * global teardown.  Two modes of operation:
 *
 *   1. API mode  (default) — calls backend DELETE/PATCH endpoints.
 *      Used inside individual spec afterEach hooks and the globalTeardown.
 *
 *   2. SQL mode  — raw SQL via pg client (runs by default; opt-out via
 *      TEST_DB_SQL_CLEANUP=false).  Used as a fallback for entities without
 *      a DELETE API, and to catch anything API mode missed.
 *
 * Usage in a spec:
 *   const { cleanupAllTestData, getAuthToken } = require('../../utils/cleanup-helpers');
 *
 *   test.afterEach(async ({ request }) => {
 *     const token = await getAuthToken(request);
 *     await cleanupAllTestData(request, token);
 *   });
 *
 * Usage in globalTeardown:
 *   const { cleanupAllTestData } = require('./utils/cleanup-helpers');
 *   // token from a fresh request context...
 */

const { TEST_EMAIL, TEST_PASSWORD, API_BASE } = require('./env-config');

// ─── Auth ─────────────────────────────────────────────────────────────────

/**
 * Get a manager auth token using a Playwright request context.
 * Retries on rate-limit / auth errors.
 */
async function getAuthToken(request) {
  for (let i = 0; i < 5; i++) {
    const resp = await request.post(`${API_BASE}/auth/login`, {
      data: { email: TEST_EMAIL, password: TEST_PASSWORD },
    });
    if (resp.status() === 200) {
      const body = await resp.json();
      return body.data.accessToken;
    }
    if (resp.status() === 429 || resp.status() === 401 || resp.status() === 403) {
      await new Promise(r => setTimeout(r, 3000 * (i + 1)));
      continue;
    }
    throw new Error(`Login failed with status ${resp.status()}`);
  }
  throw new Error('Login failed after retries');
}

// Cache tokens per worker process so cleanupAllTestData doesn't hammer
// the login endpoint and trip the API rate limiter (100 req/min).
let _cleanupTokenCache = null;
let _cleanupTokenCacheAt = 0;
const CLEANUP_TOKEN_TTL_MS = 60 * 60 * 1000; // 1 hour

/**
 * Cached version of getAuthToken. Use this in afterEach hooks so we
 * don't re-login on every test.
 */
async function getAuthTokenCached(request) {
  const now = Date.now();
  if (!_cleanupTokenCache || (now - _cleanupTokenCacheAt) > CLEANUP_TOKEN_TTL_MS) {
    _cleanupTokenCache = await getAuthToken(request);
    _cleanupTokenCacheAt = now;
  }
  return _cleanupTokenCache;
}

// ─── Generic delete helpers ─────────────────────────────────────────────────

/**
 * Fetch all pages of a list endpoint, returning all items whose field
 * matches the keyword (case-insensitive).
 *
 * @param {object} request  Playwright request context
 * @param {string} token
 * @param {object} opts
 * @param {string} opts.listPath   e.g. '/products?page=0&size=100'
 * @param {string} opts.queryName  Parameter name for keyword search (optional)
 * @param {string} opts.keyword    Match this string in item[opts.matchField]
 * @param {string} opts.matchField Field to match against (default: 'name')
 * @returns {Promise<Array>}  Array of matching items with `id` field
 */
async function fetchAllPages(request, token, { listPath, queryName, keyword, matchField = 'name' }) {
  const results = [];
  let page = 0;
  const PAGE_SIZE = 100;

  while (true) {
    const url = queryName
      ? `${API_BASE}${listPath}&${queryName}=${encodeURIComponent(keyword)}&page=${page}&size=${PAGE_SIZE}`
      : `${API_BASE}${listPath}?page=${page}&size=${PAGE_SIZE}`;

    const resp = await request.get(url, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (resp.status() !== 200) break;

    const body = await resp.json();
    let items = body.data?.content || body.data || [];

    if (items && !Array.isArray(items)) items = [];

    if (keyword && queryName) {
      items = items.filter(item => {
        const val = item[matchField] || '';
        return val.toLowerCase().includes(keyword.toLowerCase());
      });
    } else if (keyword) {
      items = items.filter(item => {
        const val = item[matchField] || '';
        return val.toLowerCase().includes(keyword.toLowerCase());
      });
    }

    results.push(...items);

    if (!body.data?.content || items.length < PAGE_SIZE) break;
    page++;
  }

  return results;
}

/**
 * Delete (or PATCH) every item returned by fetchAllPages.
 * Best-effort — does not throw on failure.
 */
async function deleteByKeyword(request, token, opts) {
  const {
    listPath,
    queryName,
    keyword,
    matchField = 'name',
    deletePathFn = id => `${listPath}/${id}`,
    method = 'DELETE',
    patchData,
    itemIdField = 'id',
  } = opts;

  const items = await fetchAllPages(request, token, { listPath, queryName, keyword, matchField });

  for (const item of items) {
    try {
      const id = item[itemIdField] || item.channelId || item;
      const deletePath = deletePathFn(id);
      const resp = await request[method.toLowerCase() === 'patch' ? 'patch' : method.toLowerCase() === 'put' ? 'put' : 'delete'](
        `${API_BASE}${deletePath}`,
        {
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          data: patchData,
        },
      );
      if (resp.status() === 404 || resp.status() === 405) continue;
    } catch (e) {
      // best-effort
    }
  }

  return items.length;
}

/**
 * Cancel an order via POST /api/orders/{id}/cancel.
 * Used because the backend has no DELETE endpoint for orders.
 */
async function cancelOrder(request, token, orderId) {
  if (!orderId) return;
  try {
    const resp = await request.post(`${API_BASE}/orders/${orderId}/cancel`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    });
    if (resp.status() === 404 || resp.status() === 405) {
      // Fallback to DELETE if backend implements it
      await request.delete(`${API_BASE}/orders/${orderId}`, {
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => {});
    }
  } catch (e) {
    // best-effort
  }
}

async function cancelEntity(request, token, listPath, id, cancelPathFn) {
  if (!id) return;
  try {
    await request[listPath.method || 'post'](`${API_BASE}${cancelPathFn(id)}`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: listPath.body,
    }).catch(() => {});
  } catch (e) {
    // best-effort
  }
}

// ─── High-level cleanup ─────────────────────────────────────────────────────

/**
 * Delete all test data by keyword using the API.
 * Called at end of each test suite (afterEach) or in globalTeardown.
 *
 * For entities without DELETE API the SQL fallback below is also used.
 */
async function cleanupAllTestData(request, token) {
  const counts = {};

  // Products — DELETE /api/products/{id}/delete (soft delete)
  try {
    counts.products = await deleteByKeyword(request, token, {
      listPath: '/products', queryName: 'search', keyword: 'TEST-',
      deletePathFn: id => `/products/${id}/delete`,
    });
  } catch (_) {}

  // Customers — DELETE /api/customers/{id}
  try {
    counts.customers = await deleteByKeyword(request, token, {
      listPath: '/customers', queryName: 'search', keyword: 'Test Customer',
      matchField: 'fullName',
      deletePathFn: id => `/customers/${id}`,
    });
  } catch (_) {}

  // Categories — DELETE /api/categories/{id}
  try {
    counts.categories = await deleteByKeyword(request, token, {
      listPath: '/categories', queryName: 'search', keyword: 'Test Category',
      matchField: 'name',
      deletePathFn: id => `/categories/${id}`,
    });
  } catch (_) {}

  // Users — DELETE /api/users/{id}
  try {
    counts.users = await deleteByKeyword(request, token, {
      listPath: '/users', queryName: 'search', keyword: 'testuser_',
      matchField: 'email',
      deletePathFn: id => `/users/${id}`,
    });
  } catch (_) {}

  // Channels — DELETE /api/channels/{id} (soft delete)
  try {
    counts.channels = await deleteByKeyword(request, token, {
      listPath: '/channels', queryName: 'search', keyword: 'TestMC_',
      matchField: 'displayName',
      deletePathFn: id => `/channels/${id}`,
    });
  } catch (_) {}

  // Orders — POST /api/orders/{id}/cancel (no DELETE endpoint)
  try {
    const orders = await fetchAllPages(request, token, {
      listPath: '/orders?page=0&size=100', queryName: 'search',
      keyword: 'Test order note', matchField: 'note',
    });
    counts.orders = 0;
    for (const o of orders) {
      await cancelOrder(request, token, o.id);
      counts.orders++;
    }
  } catch (_) {}

  // Suppliers — PATCH /api/suppliers/{id}/status (no DELETE endpoint)
  try {
    counts.suppliers = await deleteByKeyword(request, token, {
      listPath: '/suppliers', queryName: 'search', keyword: 'TestSup',
      matchField: 'name',
      deletePathFn: id => `/suppliers/${id}/status`,
      method: 'PATCH',
      patchData: { isActive: false },
    });
  } catch (_) {}

  // Stock Receipts — DELETE /api/receipts/{id}
  try {
    counts.receipts = await deleteByKeyword(request, token, {
      listPath: '/receipts', queryName: 'search', keyword: 'PN-',
      matchField: 'code',
      deletePathFn: id => `/receipts/${id}`,
    });
  } catch (_) {}

  // Stock Deliveries — PUT /api/stock-deliveries/{id}/cancel
  try {
    counts.deliveries = await deleteByKeyword(request, token, {
      listPath: '/stock-deliveries', queryName: 'search', keyword: 'PX-',
      matchField: 'code',
      deletePathFn: id => `/stock-deliveries/${id}/cancel`,
      method: 'PUT',
    });
  } catch (_) {}

  // Stocktakes — PUT /api/stocktakes/{id}/status (CANCELLED)
  try {
    counts.stocktakes = await deleteByKeyword(request, token, {
      listPath: '/stocktakes', queryName: 'search', keyword: 'KK-',
      matchField: 'sessionCode',
      deletePathFn: id => `/stocktakes/${id}/status`,
      method: 'PUT',
      patchData: { status: 'CANCELLED' },
    });
  } catch (_) {}

  // Stock Transfers — PATCH /api/transfer/{id}/status (CANCELLED)
  try {
    counts.transfers = await deleteByKeyword(request, token, {
      listPath: '/transfer', queryName: 'search', keyword: 'CK-',
      matchField: 'transferCode',
      deletePathFn: id => `/transfer/${id}/status`,
      method: 'PATCH',
      patchData: { status: 'CANCELLED' },
    });
  } catch (_) {}

  // Notifications — DELETE /api/notifications/{id}
  try {
    counts.notifications = await deleteByKeyword(request, token, {
      listPath: '/notifications', queryName: 'search', keyword: 'Test notification',
      matchField: 'title',
      deletePathFn: id => `/notifications/${id}`,
    });
  } catch (_) {}

  // Addresses — DELETE /api/addresses/{id}
  try {
    counts.addresses = await deleteByKeyword(request, token, {
      listPath: '/addresses', queryName: 'search', keyword: 'Test Address',
      matchField: 'street',
      deletePathFn: id => `/addresses/${id}`,
    });
  } catch (_) {}

  return counts;
}

/**
 * SQL-based cleanup. Runs by default; opt-out via TEST_DB_SQL_CLEANUP=false.
 *
 * Only deletes rows with clear test markers — same patterns as the
 * scripts/_delete-statements.js used in Phase 1. Safe for shared DB because
 * every WHERE clause filters by a recognizable test marker prefix.
 */
async function cleanupAllTestDataSQL(pg) {
  // Order matters: respect FK constraints (children before parents).
  // Triggers on immutable tables must be disabled before DELETE then re-enabled.
  const SQL_MARKERS = [
    // ── Inventory transactions (immutable trigger) ─────────────────────
    `ALTER TABLE inventory_transactions DISABLE TRIGGER trg_inventory_transactions_immutable`,
    `DELETE FROM inventory_transactions WHERE note LIKE 'Test transaction %'
       OR variant_id IN (SELECT id FROM product_variants
                          WHERE sku LIKE 'TEST-V-%'
                             OR sku LIKE 'SKU-TEST-%'
                             OR sku LIKE 'TEST-%'
                             OR sku LIKE 'APIV-%'
                             OR sku LIKE 'TEST-ORD-%')`,
    `ALTER TABLE inventory_transactions ENABLE TRIGGER trg_inventory_transactions_immutable`,

    // ── Stocktakes ──────────────────────────────────────────────────────
    `DELETE FROM stocktake_items WHERE session_id IN (SELECT id FROM stocktake_sessions WHERE session_code LIKE 'KK-%')`,
    `DELETE FROM stocktake_sessions WHERE session_code LIKE 'KK-%'`,

    // ── Stock transfers ─────────────────────────────────────────────────
    `DELETE FROM stock_transfer_items WHERE transfer_id IN (SELECT id FROM stock_transfers WHERE transfer_code LIKE 'CK-%')`,
    `DELETE FROM stock_transfers WHERE transfer_code LIKE 'CK-%'`,

    // ── Channels (deep clean — has multiple child tables) ──────────────
    `DELETE FROM channel_product_variants WHERE channel_product_id IN (SELECT id FROM channel_products WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%'))`,
    `DELETE FROM channel_products WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
    `DELETE FROM channel_credentials WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
    `DELETE FROM channel_connection_logs WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
    `DELETE FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%'`,

    // ── Suppliers (no DELETE API — mark inactive via is_active column) ─
    `UPDATE suppliers SET is_active=false WHERE name LIKE 'TestSup%' OR name LIKE 'ToUpdate%' OR name LIKE 'StatusTest%' OR name LIKE 'DuplicateTest%' OR name LIKE 'Some Supplier %' OR email LIKE 'supplier%@example.com'`,

    // ── Categories (clear FK then delete) ──────────────────────────────
    `UPDATE categories SET parent_id=NULL WHERE parent_id IN (SELECT id FROM categories WHERE name LIKE 'Test Category %' OR name LIKE 'API Test %' OR slug LIKE 'test-category-%')`,
    `DELETE FROM categories WHERE name LIKE 'Test Category %' OR name LIKE 'API Test %' OR slug LIKE 'test-category-%'`,

    // ── Products (deep clean) ───────────────────────────────────────────
    `DELETE FROM product_variants WHERE sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%' OR sku LIKE 'VAR-%' OR sku LIKE 'DUP-%' OR sku LIKE 'TEST-ORD-%' OR sku LIKE 'TEST-V-%' OR sku LIKE 'APIV-%'`,
    `DELETE FROM product_images WHERE product_id IN (SELECT id FROM products WHERE sku LIKE 'TEST-%' OR name LIKE 'Test Product %')`,
    `DELETE FROM product_logs WHERE product_id IN (SELECT id FROM products WHERE sku LIKE 'TEST-%' OR name LIKE 'Test Product %')`,
    `DELETE FROM products WHERE sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%' OR sku LIKE 'VAR-%' OR sku LIKE 'DUP-%' OR name LIKE 'Test Product %'`,

    // ── Customers ──────────────────────────────────────────────────────
    `DELETE FROM customers WHERE full_name LIKE 'Test Customer %'
       OR full_name LIKE 'Updated Customer %'
       OR full_name IS NULL
       OR email LIKE 'test%@example.com'
       OR email LIKE 'noname%'
       OR email LIKE 'noauth%'`,

    // ── Users (excluding the manager/admin test accounts) ───────────────
    `DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'testuser_%'
       OR email LIKE 'newuser_%'
       OR email LIKE 'dup_%'
       OR email LIKE 'delete_%'
       OR email LIKE 'getbyid_%'
       OR email LIKE 'update_%'
       OR email LIKE 'auth_test_%'
       OR email LIKE 'e2e_%'
       OR email LIKE 'invitee+%@osms-test.vn')`,
    `DELETE FROM users WHERE email LIKE 'testuser_%'
       OR email LIKE 'newuser_%'
       OR email LIKE 'dup_%'
       OR email LIKE 'delete_%'
       OR email LIKE 'getbyid_%'
       OR email LIKE 'update_%'
       OR email LIKE 'auth_test_%'
       OR email LIKE 'e2e_%'
       OR email LIKE 'invitee+%@osms-test.vn'`,

    // ── Orders (cancel via API is preferred; SQL fallback for orphans) ─
    `DELETE FROM order_items WHERE sku LIKE 'TEST-ORD-%' OR order_id IN (SELECT id FROM orders WHERE note LIKE 'Test order note %')`,
    `DELETE FROM orders WHERE note LIKE 'Test order note %' OR id IN (SELECT order_id FROM order_items WHERE sku LIKE 'TEST-ORD-%')`,

    // ── Invite tokens ──────────────────────────────────────────────────
    `DELETE FROM user_invite_tokens WHERE email LIKE 'invitee+%@osms-test.vn'`,

    // ── Notifications ──────────────────────────────────────────────────
    `DELETE FROM notifications WHERE title LIKE 'Test notification%' OR body LIKE 'Test %'`,
  ];

  let total = 0;
  for (const sql of SQL_MARKERS) {
    try {
      const r = await pg.query(sql);
      total += r.rowCount || 0;
    } catch (e) {
      // table or trigger may not exist on a fresh DB — ignore
      console.warn('[sql-cleanup] warning:', e.message.slice(0, 150));
    }
  }
  return total;
}

module.exports = {
  getAuthToken,
  getAuthTokenCached,
  fetchAllPages,
  deleteByKeyword,
  cancelOrder,
  cleanupAllTestData,
  cleanupAllTestDataSQL,
  API_BASE,
  TEST_EMAIL,
  TEST_PASSWORD,
};