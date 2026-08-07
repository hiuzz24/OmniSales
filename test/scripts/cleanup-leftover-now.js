/**
 * One-time cleanup script for leaked test data found in the audit.
 *
 * Background: customer.spec.js tests C7/C17 and user.spec.js tests
 * USR-API-6/USR-API-8 occasionally produce rows the existing cleanup
 * helpers cannot reach (server returns 200/201 with NULL fields for
 * customers, and DELETE /api/users/{id} returns 500 so the cleanup
 * attempt silently fails for users). This script removes the leaked
 * rows directly via SQL so we start from a clean state.
 *
 * Safety:
 *  - Only deletes rows matching clear test marker patterns:
 *      * users with email LIKE 'getbyid_%' | 'update_%' | 'e2e_%' | 'auth_test_%'
 *      * user_roles FK to those users
 *      * customers with full_name IS NULL | email LIKE 'noname%' | 'noauth%'
 *  - Wrapped in a transaction so it either fully commits or rolls back.
 *  - Prints the count of rows removed and a verification snapshot.
 *
 * Usage:
 *   node scripts/cleanup-leftover-now.js
 *
 * To verify-only (dry run):
 *   node scripts/cleanup-leftover-now.js --dry-run
 */

const { Client } = require('pg');

const DB_CONFIG = {
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT || 5432),
  user: process.env.DB_USERNAME || 'postgres',
  password: process.env.DB_PASSWORD || '123',
  database: process.env.DB_NAME || 'OSMS',
};

const USER_EMAIL_PATTERNS = [
  'getbyid_%',
  'update_%',
  'e2e_%',
  'auth_test_%',
];

const CUSTOMER_PATTERNS = [
  'noname%',
  'noauth%',
];

const USER_ORPHAN_SQL = `
  SELECT id, email
  FROM users
  WHERE ${USER_EMAIL_PATTERNS.map(p => `email LIKE '${p}'`).join(' OR ')}
  ORDER BY created_at DESC
`;

const CUSTOMER_ORPHAN_SQL = `
  SELECT id, full_name, email, phone
  FROM customers
  WHERE full_name IS NULL
     OR ${CUSTOMER_PATTERNS.map(p => `email LIKE '${p}'`).join(' OR ')}
  ORDER BY created_at DESC
`;

async function snapshot(client, label) {
  const r = await client.query(USER_ORPHAN_SQL);
  console.log(`[${label}] users matching test patterns: ${r.rowCount}`);
  r.rows.forEach(x => console.log(`  - ${x.email}`));
  const r2 = await client.query(CUSTOMER_ORPHAN_SQL);
  console.log(`[${label}] customers matching test patterns: ${r2.rowCount}`);
  r2.rows.forEach(x => console.log(`  - full_name=${x.full_name} | email=${x.email} | phone=${x.phone}`));
  return { users: r.rowCount, customers: r2.rowCount };
}

async function main() {
  const dryRun = process.argv.includes('--dry-run');
  const client = new Client(DB_CONFIG);
  await client.connect();

  console.log('========================================');
  console.log(`[cleanup-leftover] mode: ${dryRun ? 'DRY-RUN' : 'APPLY'}`);
  console.log(`[cleanup-leftover] target: ${DB_CONFIG.user}@${DB_CONFIG.host}:${DB_CONFIG.port}/${DB_CONFIG.database}`);
  console.log('========================================');

  console.log('\n--- BEFORE ---');
  const before = await snapshot(client, 'before');

  if (dryRun) {
    console.log('\n[cleanup-leftover] dry-run mode — no changes made.');
    await client.end();
    return;
  }

  await client.query('BEGIN');
  try {
    // Order matters: FK children before parents.
    const userPredicate = USER_EMAIL_PATTERNS.map(p => `email LIKE '${p}'`).join(' OR ');

    // 1. Drop user_roles pointing at test users
    const rRoles = await client.query(`
      DELETE FROM user_roles
      WHERE user_id IN (SELECT id FROM users WHERE ${userPredicate})
    `);
    console.log(`[cleanup-leftover] deleted ${rRoles.rowCount} user_roles rows`);

    // 2. Delete the test users themselves
    const rUsers = await client.query(`
      DELETE FROM users
      WHERE ${userPredicate}
    `);
    console.log(`[cleanup-leftover] deleted ${rUsers.rowCount} users rows`);

    // 3. Delete leaked customers (NULL full_name or test email patterns)
    const customerPredicate = CUSTOMER_PATTERNS.map(p => `email LIKE '${p}'`).join(' OR ');
    const rCustomers = await client.query(`
      DELETE FROM customers
      WHERE full_name IS NULL
         OR ${customerPredicate}
    `);
    console.log(`[cleanup-leftover] deleted ${rCustomers.rowCount} customers rows`);

    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  }

  console.log('\n--- AFTER ---');
  const after = await snapshot(client, 'after');

  console.log('\n========================================');
  console.log(`[cleanup-leftover] SUMMARY`);
  console.log(`  users before=${before.users} after=${after.users} removed=${before.users - after.users}`);
  console.log(`  customers before=${before.customers} after=${after.customers} removed=${before.customers - after.customers}`);
  console.log('========================================');

  await client.end();
}

main().catch(err => {
  console.error('[cleanup-leftover] FAILED:', err.message);
  process.exit(1);
});