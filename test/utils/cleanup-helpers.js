/**
 * test/utils/cleanup-helpers.js
 *
 * Provides helpers for cleaning up test data after each spec and for the
 * global teardown.  Two modes of operation:
 *
 *   1. API mode  (default) — calls backend DELETE/PATCH endpoints.
 *      Used inside individual spec afterEach hooks and the globalTeardown.
 *
 *   2. SQL mode  — raw SQL via pg client.
 *      Only activated when TEST_DB_SQL_CLEANUP=true (opt-in; safe for test DB).
 *      Used in globalTeardown as a fallback for entities without DELETE API.
 *
 * Usage in a spec:
 *   const { cleanupAllTestData } = require('../../utils/cleanup-helpers');
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

// ─── Generic delete helpers ─────────────────────────────────────────────────

/**
 * Fetch all pages of a list endpoint, returning all items whose field
 * matches the keyword (case-insensitive).
 *
 * @param {object} request  Playwright request context
 * @param {string} token
 * @param {object} opts
 * @param {string} opts.listPath   e.g. '/products?page=0&size=100'
 * @param {string} opts.queryName Parameter name for keyword search
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

    // Filter in-memory for keyword
    if (keyword && queryName) {
      items = items.filter(item => {
        const val = item[matchField] || '';
        return val.toLowerCase().includes(keyword.toLowerCase());
      });
    }

    results.push(...items);

    // Stop if fewer than page size returned
    if (!body.data?.content || items.length < PAGE_SIZE) break;
    page++;
  }

  return results;
}

/**
 * Delete every item returned by fetchAllPages using the per-item deleteFn.
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
  } = opts;

  const items = await fetchAllPages(request, token, { listPath, queryName, keyword, matchField });

  for (const item of items) {
    try {
      const deletePath = deletePathFn(item.id || item.channelId || item);
      const resp = await request[method.toLowerCase() === 'patch' ? 'patch' : 'delete'](
        `${API_BASE}${deletePath}`,
        {
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          data: method === 'PATCH' ? opts.patchData : undefined,
        },
      );
      if (resp.status() === 404) continue; // already gone
    } catch (e) {
      // best-effort
    }
  }

  return items.length;
}

// ─── High-level cleanup ─────────────────────────────────────────────────────

/**
 * Delete all test data by keyword using the API.
 * Called at end of each test suite (afterEach) or in globalTeardown.
 *
 * For entities without DELETE API (orders, suppliers, receipts, deliveries,
 * stocktakes, transfers) the SQL fallback below must be used.
 */
async function cleanupAllTestData(request, token) {
  const counts = {};

  try {
    counts.products = await deleteByKeyword(request, token, {
      listPath: '/products', queryName: 'search', keyword: 'TEST-',
      deletePathFn: id => `/products/${id}/delete`,
    });
  } catch (_) {}

  try {
    counts.customers = await deleteByKeyword(request, token, {
      listPath: '/customers', queryName: 'search', keyword: 'Test Customer',
      matchField: 'fullName',
      deletePathFn: id => `/customers/${id}`,
    });
  } catch (_) {}

  try {
    counts.categories = await deleteByKeyword(request, token, {
      listPath: '/categories', queryName: 'search', keyword: 'Test Category',
      matchField: 'name',
      deletePathFn: id => `/categories/${id}`,
    });
  } catch (_) {}

  try {
    counts.users = await deleteByKeyword(request, token, {
      listPath: '/users', queryName: 'search', keyword: 'testuser_',
      matchField: 'email',
      deletePathFn: id => `/users/${id}`,
    });
  } catch (_) {}

  try {
    counts.channels = await deleteByKeyword(request, token, {
      listPath: '/channels', queryName: 'search', keyword: 'TestMC_',
      matchField: 'displayName',
      deletePathFn: id => `/channels/${id}`,
    });
  } catch (_) {}

  return counts;
}

/**
 * SQL-based cleanup (opt-in via TEST_DB_SQL_CLEANUP=true).
 * Requires the `pg` package and DATABASE_URL env var.
 *
 * Only deletes rows with clear test markers — same patterns as the
 * scripts/_delete-statements.js used in Phase 1.
 */
async function cleanupAllTestDataSQL(pg) {
  const SQL_MARKERS = [
    // Inventory transactions (must disable trigger first)
    `ALTER TABLE inventory_transactions DISABLE TRIGGER trg_inventory_transactions_immutable`,
    `DELETE FROM inventory_transactions WHERE note LIKE 'Test transaction %'
       OR variant_id IN (SELECT id FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%')`,
    `ALTER TABLE inventory_transactions ENABLE TRIGGER trg_inventory_transactions_immutable`,
    // Stocktakes/transfers
    `DELETE FROM stocktake_items WHERE session_id IN (SELECT id FROM stocktake_sessions WHERE session_code LIKE 'KK-%')`,
    `DELETE FROM stocktake_sessions WHERE session_code LIKE 'KK-%'`,
    `DELETE FROM stock_transfer_items WHERE transfer_id IN (SELECT id FROM stock_transfers WHERE transfer_code LIKE 'CK-%')`,
    `DELETE FROM stock_transfers WHERE transfer_code LIKE 'CK-%'`,
    // Channels
    `DELETE FROM channel_product_variants WHERE channel_product_id IN (SELECT id FROM channel_products WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%'))`,
    `DELETE FROM channel_products WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
    `DELETE FROM channel_credentials WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
    `DELETE FROM channel_connection_logs WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
    `DELETE FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%'`,
    // Suppliers (no DELETE API — only deactivate via PATCH, SQL just marks inactive)
    `UPDATE suppliers SET status='INACTIVE' WHERE name LIKE 'TestSup%' OR name LIKE 'ToUpdate%' OR name LIKE 'StatusTest%' OR name LIKE 'DuplicateTest%' OR email LIKE 'supplier%@example.com'`,
    // Categories
    `UPDATE categories SET parent_id=NULL WHERE parent_id IN (SELECT id FROM categories WHERE name LIKE 'Test Category %' OR name LIKE 'API Test %' OR slug LIKE 'test-category-%')`,
    `DELETE FROM categories WHERE name LIKE 'Test Category %' OR name LIKE 'API Test %' OR slug LIKE 'test-category-%'`,
    // Invite tokens
    `DELETE FROM user_invite_tokens WHERE email LIKE 'invitee+%@osms-test.vn'`,
  ];

  let total = 0;
  for (const sql of SQL_MARKERS) {
    try {
      const r = await pg.query(sql);
      total += r.rowCount || 0;
    } catch (e) {
      console.warn('[sql-cleanup] warning:', e.message.slice(0, 120));
    }
  }
  return total;
}

module.exports = {
  getAuthToken,
  fetchAllPages,
  deleteByKeyword,
  cleanupAllTestData,
  cleanupAllTestDataSQL,
  API_BASE,
  TEST_EMAIL,
  TEST_PASSWORD,
};