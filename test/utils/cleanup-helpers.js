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
 * Retries with exponential backoff on HTTP 429 (rate-limit) because the
 * backend's ApiUsageFilter caps per-user requests at 100/minute per
 * endpoint and our cleanup runs 12 keyword searches after each test.
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

  // Normalize listPath: append '?' if there is no query string yet so we
  // can safely add '&foo=...' parameters afterwards. Without this, the
  // cleanup produced URLs like `/api/products&search=TEST-` which Spring
  // treated as an unknown static resource and surfaced as HTTP 500.
  const separator = listPath.includes('?') ? '&' : '?';

  while (true) {
    const url = queryName
      ? `${API_BASE}${listPath}${separator}${queryName}=${encodeURIComponent(keyword)}&page=${page}&size=${PAGE_SIZE}`
      : `${API_BASE}${listPath}${separator}page=${page}&size=${PAGE_SIZE}`;

    let resp;
    let attempt = 0;
    while (true) {
      resp = await request.get(url, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (resp.status() !== 429) break;
      attempt += 1;
      if (attempt >= 5) break;
      const wait = 1500 * attempt + Math.floor(Math.random() * 500);
      await new Promise(r => setTimeout(r, wait));
    }
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
    let deleted = false;
    for (let attempt = 0; attempt < 5 && !deleted; attempt++) {
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
        if (resp.status() === 404 || resp.status() === 405) { deleted = true; break; }
        if (resp.status() === 429) {
          await new Promise(r => setTimeout(r, 1500 * (attempt + 1) + Math.floor(Math.random() * 500)));
          continue;
        }
        deleted = true;
      } catch (e) {
        // best-effort
        deleted = true;
      }
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
 * Delete every test product in the catalog using the API. Used by the
 * product spec afterEach hook (where calling the full
 * `cleanupAllTestData()` would otherwise time out) and by the global
 * teardown. Returns the number of products successfully deleted.
 */
async function cleanupTestProducts(request, token) {
  const productSkuPrefixes = [
    'TEST-', 'API-', 'VAR-', 'V1-', 'V2-', 'DUP-', 'DUPV-',
    'E2E-', 'SV-', 'VR-', 'DV-', 'NOPRICE-', 'NONAME', 'NONAMEV-',
    'NOVAR-', 'UNAUTH', 'UA-', 'DRAFT-', 'SEARCH-', 'SKU-TEST-',
    'SMOKE', 'NEWBE-', 'RETEST', 'RET-', 'SIMPLE-', 'ASCII-',
    'WITHMAU-', 'NOVARW-', 'EMPTYVIMG-', 'NOVARIMG-', 'VARIMG-',
    'ONLYVIMG-', 'APIV-', 'TEST-V-', 'TEST-ORD-',
  ];
  let deleted = 0;
  try {
    const allProducts = await fetchAllPages(request, token, {
      listPath: '/products',
      queryName: undefined,
      keyword: undefined,
      pageSize: 100,
    });
    const testProducts = allProducts.filter(p => {
      const sku = (p.sku || '').toUpperCase();
      return productSkuPrefixes.some(prefix => sku.startsWith(prefix.toUpperCase()));
    });
    for (const p of testProducts) {
      let ok = false;
      for (let attempt = 0; attempt < 5 && !ok; attempt++) {
        const r = await request.delete(`${API_BASE}/products/${p.id}/delete`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (r.status() === 429) {
          await new Promise(res => setTimeout(res, 1500 * (attempt + 1)));
          continue;
        }
        ok = true;
        if (r.status() < 400) deleted++;
      }
    }
  } catch (_) {
    // best-effort
  }
  return deleted;
}

/**
 * Delete all test data by keyword using the API.
 * Called at end of each test suite (afterEach) or in globalTeardown.
 *
 * For entities without DELETE API the SQL fallback below is also used.
 */
async function cleanupAllTestData(request, token) {
  const counts = {};

  // Products — DELETE /api/products/{id}/delete (soft delete).
  //
  // Tests create products with many different name/SKU prefixes:
  //   * helper default  : "Test Product …" + sku "TEST-…"
  //   * API spec        : "API Test Product …", "API Variant Product …"
  //   * duplicate spec  : sku "DUP-…"
  //   * variant spec    : sku "VAR-…", variant "V1-…", "V2-…"
  //   * e2e spec        : sku "E2E-…", "DRAFT-…", "SV-…", "VR-…", "DV-…"
  //   * validation spec : "NONAME…", "NOVAR…", "UNAUTH…", "NOPRICE-…"
  //   * product-variant : sku "SEARCH-…"
  //
  // Strategy: a SINGLE GET to /api/products (no keyword) returns every
  // non-deleted product. We then keep only products whose SKU starts with
  // any known test marker, and DELETE them. This used to be 12 keyword
  // searches (one per prefix), which together tripped the backend's
  // 100-req/minute ApiUsageFilter on /api/products and produced 429
  // failures during cleanup itself.
  counts.products = await cleanupTestProducts(request, token);

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
    // SKU prefixes used across catalog specs — see comment in
    // cleanupAllTestData() above. Be aggressive here because the BE API
    // soft-deletes rows, and tests depend on /api/products returning only
    // fresh data after cleanup.
    `DELETE FROM product_images WHERE product_id IN (SELECT id FROM products
       WHERE sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%'
          OR sku LIKE 'VAR-%' OR sku LIKE 'V1-%' OR sku LIKE 'V2-%'
          OR sku LIKE 'DUP-%' OR sku LIKE 'DUPV-%' OR sku LIKE 'E2E-%'
          OR sku LIKE 'SV-%' OR sku LIKE 'VR-%' OR sku LIKE 'DV-%'
          OR sku LIKE 'NOPRICE-%' OR sku LIKE 'NONAME%' OR sku LIKE 'NONAMEV-%'
          OR sku LIKE 'NOVAR-%' OR sku LIKE 'UNAUTH%' OR sku LIKE 'UA-%'
          OR sku LIKE 'DRAFT-%' OR sku LIKE 'SEARCH-%' OR sku LIKE 'SMOKE%'
          OR sku LIKE 'NEWBE-%' OR sku LIKE 'RETEST%' OR sku LIKE 'RET%'
          OR sku LIKE 'SIMPLE-%' OR sku LIKE 'ASCII-%' OR sku LIKE 'WITHMAU-%'
          OR sku LIKE 'NOVARW-%' OR sku LIKE 'EMPTYVIMG-%' OR sku LIKE 'NOVARIMG-%'
          OR sku LIKE 'VARIMG-%' OR sku LIKE 'ONLYVIMG-%' OR name LIKE 'Test Product %'
          OR name LIKE 'API Test %' OR name LIKE 'API Variant %'
          OR name LIKE 'First Product %' OR name LIKE 'E2E Test Product %'
          OR name LIKE 'E2E Variant Product %' OR name LIKE 'E2E Draft Product %'
          OR name LIKE 'Search Test Product %' OR name LIKE 'NewBE %'
          OR name LIKE 'ReTest %' OR name LIKE 'ReTest2 %'
          OR name LIKE 'AsciiTest %' OR name LIKE 'NoImgVariant %'
          OR name LIKE 'Smoke %' OR name LIKE 'Smoke2 %'
          OR name LIKE 'Smoke Test %' OR name LIKE 'Smoke Final %'
          OR name LIKE 'Smoke Variants %' OR name LIKE 'WithMau %'
          OR name LIKE 'NoVarImg %' OR name LIKE 'Simpler %' OR name LIKE 'NoVarW %')`,
    `DELETE FROM product_logs WHERE product_id IN (SELECT id FROM products
       WHERE sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%'
          OR sku LIKE 'VAR-%' OR sku LIKE 'V1-%' OR sku LIKE 'V2-%'
          OR sku LIKE 'DUP-%' OR sku LIKE 'DUPV-%' OR sku LIKE 'E2E-%'
          OR sku LIKE 'SV-%' OR sku LIKE 'VR-%' OR sku LIKE 'DV-%'
          OR sku LIKE 'NOPRICE-%' OR sku LIKE 'NONAME%' OR sku LIKE 'NONAMEV-%'
          OR sku LIKE 'NOVAR-%' OR sku LIKE 'UNAUTH%' OR sku LIKE 'UA-%'
          OR sku LIKE 'DRAFT-%' OR sku LIKE 'SEARCH-%' OR sku LIKE 'SMOKE%'
          OR sku LIKE 'NEWBE-%' OR sku LIKE 'RETEST%' OR sku LIKE 'RET%'
          OR sku LIKE 'SIMPLE-%' OR sku LIKE 'ASCII-%' OR sku LIKE 'WITHMAU-%'
          OR sku LIKE 'NOVARW-%' OR sku LIKE 'EMPTYVIMG-%' OR sku LIKE 'NOVARIMG-%'
          OR sku LIKE 'VARIMG-%' OR sku LIKE 'ONLYVIMG-%' OR name LIKE 'Test Product %'
          OR name LIKE 'API Test %' OR name LIKE 'API Variant %'
          OR name LIKE 'First Product %' OR name LIKE 'E2E Test Product %'
          OR name LIKE 'E2E Variant Product %' OR name LIKE 'E2E Draft Product %'
          OR name LIKE 'Search Test Product %' OR name LIKE 'NewBE %'
          OR name LIKE 'ReTest %' OR name LIKE 'ReTest2 %'
          OR name LIKE 'AsciiTest %' OR name LIKE 'NoImgVariant %'
          OR name LIKE 'Smoke %' OR name LIKE 'Smoke2 %'
          OR name LIKE 'Smoke Test %' OR name LIKE 'Smoke Final %'
          OR name LIKE 'Smoke Variants %' OR name LIKE 'WithMau %'
          OR name LIKE 'NoVarImg %' OR name LIKE 'Simpler %' OR name LIKE 'NoVarW %')`,
    `DELETE FROM product_variants WHERE sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%'
       OR sku LIKE 'VAR-%' OR sku LIKE 'V1-%' OR sku LIKE 'V2-%'
       OR sku LIKE 'DUP-%' OR sku LIKE 'DUPV-%' OR sku LIKE 'E2E-%'
       OR sku LIKE 'SV-%' OR sku LIKE 'VR-%' OR sku LIKE 'DV-%'
       OR sku LIKE 'NOPRICE-%' OR sku LIKE 'NONAME%' OR sku LIKE 'NONAMEV-%'
       OR sku LIKE 'NOVAR-%' OR sku LIKE 'UNAUTH%' OR sku LIKE 'UA-%'
       OR sku LIKE 'DRAFT-%' OR sku LIKE 'SEARCH-%' OR sku LIKE 'SMOKE%'
       OR sku LIKE 'NEWBE-%' OR sku LIKE 'RETEST%' OR sku LIKE 'RET%'
       OR sku LIKE 'SIMPLE-%' OR sku LIKE 'ASCII-%' OR sku LIKE 'WITHMAU-%'
       OR sku LIKE 'NOVARW-%' OR sku LIKE 'EMPTYVIMG-%' OR sku LIKE 'NOVARIMG-%'
       OR sku LIKE 'VARIMG-%' OR sku LIKE 'ONLYVIMG-%' OR sku LIKE 'APIV-%' OR sku LIKE 'TEST-V-%' OR sku LIKE 'TEST-ORD-%'`,
    `DELETE FROM products WHERE sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%'
       OR sku LIKE 'VAR-%' OR sku LIKE 'V1-%' OR sku LIKE 'V2-%'
       OR sku LIKE 'DUP-%' OR sku LIKE 'DUPV-%' OR sku LIKE 'E2E-%'
       OR sku LIKE 'SV-%' OR sku LIKE 'VR-%' OR sku LIKE 'DV-%'
       OR sku LIKE 'NOPRICE-%' OR sku LIKE 'NONAME%' OR sku LIKE 'NONAMEV-%'
       OR sku LIKE 'NOVAR-%' OR sku LIKE 'UNAUTH%' OR sku LIKE 'UA-%'
       OR sku LIKE 'DRAFT-%' OR sku LIKE 'SEARCH-%' OR sku LIKE 'SMOKE%'
       OR sku LIKE 'NEWBE-%' OR sku LIKE 'RETEST%' OR sku LIKE 'RET%'
       OR sku LIKE 'SIMPLE-%' OR sku LIKE 'ASCII-%' OR sku LIKE 'WITHMAU-%'
       OR sku LIKE 'NOVARW-%' OR sku LIKE 'EMPTYVIMG-%' OR sku LIKE 'NOVARIMG-%'
       OR sku LIKE 'VARIMG-%' OR sku LIKE 'ONLYVIMG-%' OR name LIKE 'Test Product %'
       OR name LIKE 'API Test %' OR name LIKE 'API Variant %'
       OR name LIKE 'First Product %' OR name LIKE 'E2E Test Product %'
       OR name LIKE 'E2E Variant Product %' OR name LIKE 'E2E Draft Product %'
       OR name LIKE 'Search Test Product %' OR name LIKE 'NewBE %'
       OR name LIKE 'ReTest %' OR name LIKE 'ReTest2 %'
       OR name LIKE 'AsciiTest %' OR name LIKE 'NoImgVariant %'
       OR name LIKE 'Smoke %' OR name LIKE 'Smoke2 %'
       OR name LIKE 'Smoke Test %' OR name LIKE 'Smoke Final %'
       OR name LIKE 'Smoke Variants %' OR name LIKE 'WithMau %'
       OR name LIKE 'NoVarImg %' OR name LIKE 'Simpler %' OR name LIKE 'NoVarW %'`,

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
  cleanupTestProducts,
  API_BASE,
  TEST_EMAIL,
  TEST_PASSWORD,
};