/**
 * CLI wrapper around cleanup-helpers.cleanupAllTestData().
 * Use this to manually clean up test data without running the full test
 * suite — useful in dev when the test data accumulates between runs.
 *
 *   node scripts/cleanup-test-data.js           # API mode (default)
 *   node scripts/cleanup-test-data.js --sql     # SQL mode (uses pg client)
 *
 * The SQL mode requires TEST_DB_SQL_CLEANUP=true env (or pass --force-sql).
 */

const { cleanupAllTestData, getAuthToken } = require('../utils/cleanup-helpers');
const { API_BASE, TEST_EMAIL, TEST_PASSWORD } = require('../utils/env-config');

async function apiMode() {
  const request = await (require('@playwright/test').request).newContext();
  try {
    console.log('[cleanup] login as', TEST_EMAIL);
    const token = await getAuthToken(request);
    const counts = await cleanupAllTestData(request, token);
    console.log('[cleanup] API mode results:', counts);
  } finally {
    await request.dispose();
  }
}

async function sqlMode() {
  if (process.env.TEST_DB_SQL_CLEANUP !== 'true' && !process.argv.includes('--force-sql')) {
    console.error(
      '[cleanup] SQL mode requires TEST_DB_SQL_CLEANUP=true or --force-sql.\n' +
      '[cleanup] Refusing to run on what may be a shared dev DB.\n' +
      '[cleanup] Re-run with: TEST_DB_SQL_CLEANUP=true node scripts/cleanup-test-data.js --sql',
    );
    process.exit(2);
  }
  const pg = require('pg');
  const { cleanupAllTestDataSQL } = require('../utils/cleanup-helpers');
  const DATABASE_URL =
    process.env.DATABASE_URL ||
    `postgresql://${process.env.DB_USERNAME || 'postgres'}:${process.env.DB_PASSWORD || '123'}@${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}/${process.env.DB_NAME || 'OSMS'}`;
  const client = new pg.Client({ connectionString: DATABASE_URL });
  console.log('[cleanup] SQL mode connecting...');
  await client.connect();
  try {
    const n = await cleanupAllTestDataSQL(client);
    console.log('[cleanup] SQL cleanup removed', n, 'rows');
  } finally {
    await client.end();
  }
}

async function main() {
  const args = process.argv.slice(2);
  if (args.includes('--sql')) {
    await sqlMode();
  } else {
    await apiMode();
  }
}

main().catch(err => {
  console.error('[cleanup] FAILED:', err.message);
  process.exit(1);
});