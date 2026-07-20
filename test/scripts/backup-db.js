/**
 * Phase 1.1 — Backup the OSMS database before test-data cleanup.
 *
 * Uses the Node.js `pg` client to:
 *   1. Open a connection to the OSMS database.
 *   2. Stream every table's data + DDL using pg_dump-style `COPY ... TO STDOUT`.
 *   3. Write the SQL dump to `backend/backups/osms_pre_cleanup_<timestamp>.sql`.
 *
 * Connection params (host/port/user/password/db) come from DATABASE_URL env
 * var or individual DB_* vars; otherwise the Spring Boot defaults are used.
 *
 * Run with:  node scripts/backup-db.js
 */

const fs = require('node:fs');
const path = require('node:path');
const { Client } = require('pg');

const HOST = process.env.DB_HOST || 'localhost';
const PORT = parseInt(process.env.DB_PORT || '5432', 10);
const USER = process.env.DB_USERNAME || process.env.DB_USER || 'postgres';
const PASSWORD = process.env.DB_PASSWORD || '123';
const DATABASE = process.env.DB_NAME || 'OSMS';

const PROJECT_ROOT = path.resolve(__dirname, '..', '..', '..');
const BACKUP_DIR = path.join(PROJECT_ROOT, 'backend', 'backups');

const TABLES_IN_DUMP_ORDER = [
  // children first so COPY ... TO STDOUT doesn't violate FK ordering
  'user_roles',
  'refresh_tokens',
  'password_reset_tokens',
  'user_invite_tokens',
  'categories',
  'countries',
  'administrative_divisions',
  'product_variants',
  'product_images',
  'product_logs',
  'channel_credentials',
  'channel_connection_logs',
  'channel_products',
  'channel_product_variants',
  'customer_platform_ids',
  'customers',
  'warehouses',
  'inventory_receipt_items',
  'inventory_receipts',
  'inventory_issue_items',
  'inventory_issues',
  'inventory_items',
  'inventory_transactions',
  'stock_transfer_items',
  'stock_transfers',
  'stocktake_items',
  'stocktake_sessions',
  'order_items',
  'orders',
  'suppliers',
  'products',
  'channels',
  'notifications',
  'audit_logs',
  'system_logs',
  'api_metrics_daily',
  'api_endpoint_limits',
  'system_settings',
  'sync_tasks',
  'sync_logs',
  'webhook_events',
  'daily_sales_summary',
  'report_configs',
  'report_results',
  'roles',
  'users',
  'backup_files',
];

async function dumpTable(client, tableName, out) {
  // Detect column list dynamically so the dump works even if a column is added.
  const colRes = await client.query(
    `SELECT column_name, data_type
       FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = $1
      ORDER BY ordinal_position`,
    [tableName],
  );
  if (colRes.rowCount === 0) {
    out.write(`-- table ${tableName} not found, skipping\n`);
    return 0;
  }

  const cols = colRes.rows.map(r => r.column_name);
  const colList = cols.map(c => `"${c}"`).join(', ');
  out.write(`\n-- ${tableName} (${colRes.rowCount} cols)\n`);
  out.write(`COPY ${tableName} (${colList}) FROM stdin;\n`);

  const res = await client.query(
    `SELECT ${colList} FROM ${tableName}`,
  );

  for (const row of res.rows) {
    const line = cols
      .map(c => {
        const v = row[c];
        if (v === null || v === undefined) return '\\N';
        if (v instanceof Date) return v.toISOString();
        if (typeof v === 'object') return JSON.stringify(v);
        // Escape backslash and newline for COPY text format.
        return String(v)
          .replace(/\\/g, '\\\\')
          .replace(/\n/g, '\\n')
          .replace(/\r/g, '\\r')
          .replace(/\t/g, '\\t');
      })
      .join('\t');
    out.write(line + '\n');
  }

  out.write(`\\.\n`);
  return res.rowCount;
}

async function main() {
  if (!fs.existsSync(BACKUP_DIR)) {
    fs.mkdirSync(BACKUP_DIR, { recursive: true });
  }

  const stamp = new Date()
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\..+$/, '')
    .replace('T', '_');
  const outFile = path.join(BACKUP_DIR, `osms_pre_cleanup_${stamp}.sql`);

  const client = new Client({
    host: HOST,
    port: PORT,
    user: USER,
    password: PASSWORD,
    database: DATABASE,
  });

  console.log(`[backup] connecting to ${USER}@${HOST}:${PORT}/${DATABASE}`);
  await client.connect();

  const out = fs.createWriteStream(outFile, { encoding: 'utf8' });
  out.write(`-- OSMS pre-cleanup backup\n`);
  out.write(`-- generated at ${new Date().toISOString()}\n`);
  out.write(`-- source: ${USER}@${HOST}:${PORT}/${DATABASE}\n`);
  out.write(`SET client_min_messages = WARNING;\n`);
  out.write(`BEGIN;\n`);

  let totalRows = 0;
  const tableRowCounts = {};
  for (const table of TABLES_IN_DUMP_ORDER) {
    try {
      const n = await dumpTable(client, table, out);
      tableRowCounts[table] = n;
      totalRows += n;
    } catch (e) {
      out.write(`-- error dumping ${table}: ${e.message}\n`);
      tableRowCounts[table] = -1;
    }
  }

  out.write(`COMMIT;\n`);
  out.end();

  await new Promise(resolve => out.on('finish', resolve));
  await client.end();

  console.log('[backup] row counts:');
  for (const [t, n] of Object.entries(tableRowCounts)) {
    console.log(`  ${t.padEnd(30)} ${n}`);
  }
  console.log(`[backup] total rows dumped: ${totalRows}`);
  console.log(`[backup] file: ${outFile}`);
  console.log(`[backup] size: ${(fs.statSync(outFile).size / 1024).toFixed(1)} KB`);
}

main().catch(err => {
  console.error('[backup] FAILED:', err.message);
  process.exit(1);
});