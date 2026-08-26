/**
 * Global teardown — chạy một lần sau khi tất cả Playwright tests kết thúc.
 *
 * Nhiệm vụ:
 *   1. Dọn dẹp API qua cleanup-helpers (luôn chạy).
 *   2. Dọn dẹp SQL — chạy mặc định; tắt qua TEST_DB_SQL_CLEANUP=false.
 *      Chỉ xóa các rows có test markers (TEST-*, TestMC_*, KK-*, CK-*, TestSup%, ...),
 *      nên an toàn khi chạy trên shared dev DB.
 *
 * globalTeardown chạy bên ngoài test context, nên tạo Playwright
 * request context riêng qua @playwright/test's request API.
 */

const { cleanupAllTestData, cleanupAllTestDataSQL, getAuthToken } = require('./utils/cleanup-helpers');

// pg is only needed for SQL cleanup mode
const pg = require('pg');

module.exports = async () => {
  console.log('[teardown] bắt đầu lúc', new Date().toISOString());

  // ── Dọn dẹp API (luôn chạy) ────────────────────────────────────────
  try {
    const request = await (require('@playwright/test').request).newContext();
    const token = await getAuthToken(request);
    const counts = await cleanupAllTestData(request, token);
    const deleted = Object.values(counts).reduce((s, n) => s + n, 0);
    console.log('[teardown] dọn dẹp API xong —', deleted, 'items đã xóa', counts);
    await request.dispose();
  } catch (e) {
    console.warn('[teardown] dọn dẹp API thất bại:', e.message);
  }

  // ── Dọn dẹp SQL (mặc định bật, tắt qua TEST_DB_SQL_CLEANUP=false) ──
  const skipSql = process.env.TEST_DB_SQL_CLEANUP === 'false';
  if (skipSql) {
    console.log('[teardown] TEST_DB_SQL_CLEANUP=false — bỏ qua dọn dẹp SQL');
  } else {
    const dbConfig = {
      host: process.env.DB_HOST || 'localhost',
      port: Number(process.env.DB_PORT || 5432),
      user: process.env.DB_USERNAME || 'postgres',
      password: process.env.DB_PASSWORD || '123',
      database: process.env.DB_NAME || 'OSMS',
    };

    console.log('[teardown] bắt đầu dọn dẹp SQL (chỉ xóa marker test, an toàn cho shared DB)');
    const client = new pg.Client(dbConfig);
    try {
      await client.connect();
      const total = await cleanupAllTestDataSQL(client);
      console.log('[teardown] dọn dẹp SQL xong —', total, 'rows đã xóa');
    } catch (e) {
      console.warn('[teardown] dọn dẹp SQL thất bại:', e.message);
    } finally {
      await client.end();
    }
  }

  console.log('[teardown] kết thúc lúc', new Date().toISOString());
};