/**
 * Global teardown — runs once after all Playwright tests finish.
 *
 * Responsibilities:
 *   1. API cleanup via cleanup-helpers (always runs).
 *   2. SQL cleanup — runs by default; opt-out via TEST_DB_SQL_CLEANUP=false.
 *      The SQL cleanup only removes rows whose names/SKUs/codes match known
 *      test markers (TEST-*, TestMC_*, KK-*, CK-*, TestSup%, ...), so it is
 *      safe to run on a shared dev DB.
 *
 * The globalTeardown runs outside the test context, so it creates its own
 * Playwright request context via @playwright/test's request API.
 */

const { cleanupAllTestData, cleanupAllTestDataSQL, getAuthToken } = require('./utils/cleanup-helpers');

// pg is only needed for SQL cleanup mode
const pg = require('pg');

module.exports = async () => {
  console.log('[teardown] starting at', new Date().toISOString());

  // ── API cleanup (always runs) ────────────────────────────────────────
  try {
    const request = await (require('@playwright/test').request).newContext();
    const token = await getAuthToken(request);
    const counts = await cleanupAllTestData(request, token);
    const deleted = Object.values(counts).reduce((s, n) => s + n, 0);
    console.log('[teardown] API cleanup done —', deleted, 'items removed', counts);
    await request.dispose();
  } catch (e) {
    console.warn('[teardown] API cleanup failed:', e.message);
  }

  // ── SQL cleanup (default ON, opt-out via TEST_DB_SQL_CLEANUP=false) ──
  const skipSql = process.env.TEST_DB_SQL_CLEANUP === 'false';
  if (skipSql) {
    console.log('[teardown] TEST_DB_SQL_CLEANUP=false — skipping SQL cleanup');
  } else {
    const dbConfig = {
      host: process.env.DB_HOST || 'localhost',
      port: Number(process.env.DB_PORT || 5432),
      user: process.env.DB_USERNAME || 'postgres',
      password: process.env.DB_PASSWORD || '123',
      database: process.env.DB_NAME || 'OSMS',
    };

    console.log('[teardown] SQL cleanup starting (marker-only delete, safe for shared DB)');
    const client = new pg.Client(dbConfig);
    try {
      await client.connect();
      const total = await cleanupAllTestDataSQL(client);
      console.log('[teardown] SQL cleanup done —', total, 'rows removed');
    } catch (e) {
      console.warn('[teardown] SQL cleanup failed:', e.message);
    } finally {
      await client.end();
    }
  }

  console.log('[teardown] finished at', new Date().toISOString());
};