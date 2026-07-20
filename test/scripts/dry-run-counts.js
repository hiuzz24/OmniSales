/**
 * Phase 1.2 — Dry-run: count rows matching test-data markers.
 *
 * Only runs SELECTs, never DELETEs. Prints a table to stdout so the
 * developer can sanity-check which rows would be removed before
 * Phase 1.3 runs.
 *
 * Run with:  node scripts/dry-run-counts.js
 */

const { Client } = require('pg');

const HOST = process.env.DB_HOST || 'localhost';
const PORT = parseInt(process.env.DB_PORT || '5432', 10);
const USER = process.env.DB_USERNAME || process.env.DB_USER || 'postgres';
const PASSWORD = process.env.DB_PASSWORD || '123';
const DATABASE = process.env.DB_NAME || 'OSMS';

const QUERIES = [
  {
    label: 'product.name',
    sql: `SELECT count(*) FROM products
          WHERE name LIKE 'Test Product %'
             OR name LIKE 'API Test %'
             OR name LIKE 'Updated Product %'
             OR name LIKE 'First Product %'`,
  },
  {
    label: 'product.sku',
    sql: `SELECT count(*) FROM products
          WHERE sku LIKE 'TEST-%'
             OR sku LIKE 'SKU-TEST-%'
             OR sku LIKE 'API-%'
             OR sku LIKE 'VAR-%'
             OR sku LIKE 'DUP-%'
             OR sku LIKE 'NONAME-%'
             OR sku LIKE 'UNAUTH-%'`,
  },
  {
    label: 'product_variants.sku',
    sql: `SELECT count(*) FROM product_variants
          WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%'`,
  },
  {
    label: 'customer.full_name/email',
    sql: `SELECT count(*) FROM customers
          WHERE full_name LIKE 'Test Customer %'
             OR full_name LIKE 'Updated Customer %'
             OR email LIKE 'test%@example.com'
             OR email LIKE 'noauth%@example.com'
             OR email LIKE 'noname%@example.com'
             OR email LIKE 'updated%@example.com'`,
  },
  {
    label: 'users.email',
    sql: `SELECT count(*) FROM users
          WHERE (email LIKE 'testuser_%@test.com'
              OR email LIKE 'newuser_%@test.com'
              OR email LIKE 'dup_%@test.com'
              OR email LIKE 'delete_%@test.com'
              OR email LIKE 'update_%@test.com'
              OR email LIKE 'getbyid_%@test.com'
              OR email LIKE 'auth_test_%@test.com'
              OR email LIKE 'e2e_%@test.com'
              OR email LIKE 'invitee+%@osms-test.vn')
            AND email NOT IN ('admin@osms.vn', 'manager@osms.vn')`,
  },
  {
    label: 'category.name/slug',
    sql: `SELECT count(*) FROM categories
          WHERE name LIKE 'Test Category %'
             OR name LIKE 'API Test %'
             OR slug LIKE 'test-category-%'`,
  },
  {
    label: 'order.note',
    sql: `SELECT count(*) FROM orders
          WHERE note LIKE 'Test order note %' OR note LIKE 'Test Item%'`,
  },
  {
    label: 'order_items.sku',
    sql: `SELECT count(*) FROM order_items WHERE sku LIKE 'TEST-ORD-%'`,
  },
  {
    label: 'supplier.name/email',
    sql: `SELECT count(*) FROM suppliers
          WHERE name LIKE 'TestSup%'
             OR name LIKE 'ToUpdate%'
             OR name LIKE 'StatusTest%'
             OR name LIKE 'DuplicateTest%'
             OR email LIKE 'supplier%@example.com'`,
  },
  {
    label: 'channel.display_name',
    sql: `SELECT count(*) FROM channels
          WHERE display_name LIKE 'TestMC_%'
             OR display_name LIKE 'BadCommission %'
             OR display_name LIKE 'Updated Manual Channel %'
             OR display_name LIKE 'ToDelete_%'
             OR display_name LIKE 'DupCh_%'`,
  },
  {
    label: 'stocktake_sessions.session_code',
    sql: `SELECT count(*) FROM stocktake_sessions WHERE session_code LIKE 'KK-%'`,
  },
  {
    label: 'stock_transfers.transfer_code',
    sql: `SELECT count(*) FROM stock_transfers WHERE transfer_code LIKE 'CK-%'`,
  },
  {
    label: 'inventory_receipts.notes=test',
    sql: `SELECT count(*) FROM inventory_receipts
          WHERE notes = 'Updated by test'`,
  },
  {
    label: 'inventory_issues.notes=Updated note',
    sql: `SELECT count(*) FROM inventory_issues
          WHERE notes = 'Updated note'`,
  },
  {
    label: 'inventory_transactions.note',
    sql: `SELECT count(*) FROM inventory_transactions
          WHERE note LIKE 'Test transaction %'`,
  },
  {
    label: 'user_invite_tokens.email',
    sql: `SELECT count(*) FROM user_invite_tokens WHERE email LIKE 'invitee+%@osms-test.vn'`,
  },
  {
    label: 'notifications.by_test_user',
    sql: `SELECT count(*) FROM notifications
          WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'testuser_%@test.com')`,
  },
  {
    label: 'audit_logs.by_test_user',
    sql: `SELECT count(*) FROM audit_logs
          WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'testuser_%@test.com')`,
  },
];

async function main() {
  const client = new Client({ host: HOST, port: PORT, user: USER, password: PASSWORD, database: DATABASE });
  await client.connect();
  console.log(`[dry-run] ${USER}@${HOST}:${PORT}/${DATABASE}`);
  console.log('-'.repeat(60));
  console.log('marker'.padEnd(35) + 'rows');
  console.log('-'.repeat(60));

  let total = 0;
  let nonZeroCount = 0;
  for (const q of QUERIES) {
    try {
      const r = await client.query(q.sql);
      const n = parseInt(r.rows[0].count, 10);
      if (n > 0) nonZeroCount++;
      total += n;
      console.log(q.label.padEnd(35) + n);
    } catch (e) {
      console.log(q.label.padEnd(35) + 'ERR ' + e.message);
    }
  }

  console.log('-'.repeat(60));
  console.log(`TOTAL`.padEnd(35) + total);
  console.log(`non-zero markers: ${nonZeroCount} / ${QUERIES.length}`);

  if (total === 0) {
    console.log('[dry-run] DB already clean — nothing to delete.');
  } else {
    console.log('[dry-run] review the counts above. If everything looks like test data,');
    console.log('[dry-run] run scripts/delete-test-data.js next (Phase 1.3).');
  }

  await client.end();
}

main().catch(err => {
  console.error('[dry-run] FAILED:', err.message);
  process.exit(1);
});