/**
 * Global teardown — runs once after all Playwright tests finish.
 *
 * Responsibilities:
 *   1. API cleanup via cleanup-helpers (works on any DB).
 *   2. SQL cleanup — optional, enabled only when TEST_DB_SQL_CLEANUP=true.
 *      WARNING: never enable on a shared dev DB; designed for dedicated test DBs.
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

  // ── SQL cleanup (opt-in) ──────────────────────────────────────────────
  if (process.env.TEST_DB_SQL_CLEANUP === 'true') {
    const DATABASE_URL =
      process.env.DATABASE_URL ||
      `postgresql://${process.env.DB_USERNAME || 'postgres'}:${process.env.DB_PASSWORD || '123'}@${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}/${process.env.DB_NAME || 'OSMS'}`;

    console.log('[teardown] TEST_DB_SQL_CLEANUP=true — starting SQL cleanup');
    const client = new pg.Client({ connectionString: DATABASE_URL });
    try {
      await client.connect();
      const total = await cleanupAllTestDataSQL(client);
      console.log('[teardown] SQL cleanup done —', total, 'rows removed');
    } catch (e) {
      console.warn('[teardown] SQL cleanup failed:', e.message);
    } finally {
      await client.end();
    }
  } else {
    console.log('[teardown] TEST_DB_SQL_CLEANUP not set — skipping SQL cleanup');
  }

  console.log('[teardown] finished at', new Date().toISOString());
};