/**
 * Phase 1.3 — Delete test-data rows inside a single transaction.
 *
 * Usage:
 *   node scripts/delete-test-data.js           # prompts for confirmation
 *   node scripts/delete-test-data.js --yes     # skip confirmation (auto COMMIT)
 *   node scripts/delete-test-data.js --dry-run # only print SQL, do nothing
 *
 * All DELETEs run inside one transaction; the script prints ROLLBACK or
 * COMMIT based on the result.
 */

const { Client } = require('pg');
const readline = require('node:readline');
const { STATEMENTS } = require('./_delete-statements');

const HOST = process.env.DB_HOST || 'localhost';
const PORT = parseInt(process.env.DB_PORT || '5432', 10);
const USER = process.env.DB_USERNAME || process.env.DB_USER || 'postgres';
const PASSWORD = process.env.DB_PASSWORD || '123';
const DATABASE = process.env.DB_NAME || 'OSMS';

async function prompt(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise(resolve => rl.question(question, ans => {
    rl.close();
    resolve(ans);
  }));
}

async function main() {
  const args = process.argv.slice(2);
  const isDryRun = args.includes('--dry-run');
  const skipConfirm = args.includes('--yes');
  const isValidate = args.includes('--validate');

  const client = new Client({ host: HOST, port: PORT, user: USER, password: PASSWORD, database: DATABASE });
  console.log(`[delete] ${USER}@${HOST}:${PORT}/${DATABASE}`);
  console.log(`[delete] ${STATEMENTS.length} DELETE statements queued`);

  if (isDryRun) {
    console.log('[delete] --dry-run mode — printing SQL only:');
    for (const sql of STATEMENTS) console.log(`  ${sql}`);
    console.log('[delete] dry-run complete, no changes made.');
    return;
  }

  if (isValidate) {
    console.log('[delete] --validate mode — runs inside BEGIN, then ROLLBACKs.');
    await client.connect();
    try {
      await client.query('BEGIN');
      let total = 0;
      for (let i = 0; i < STATEMENTS.length; i++) {
        const sql = STATEMENTS[i];
        const r = await client.query(sql);
        const n = r.rowCount || 0;
        total += n;
        console.log(`  ${String(i + 1).padStart(2)}/${STATEMENTS.length}  ${n.toString().padStart(5)}  ${sql.slice(0, 70)}`);
      }
      await client.query('ROLLBACK');
      console.log(`[delete] VALIDATE OK — ${total} rows would be deleted, ROLLED BACK.`);
    } catch (e) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('[delete] VALIDATE FAILED at step above:', e.message);
      process.exitCode = 1;
    } finally {
      await client.end();
    }
    return;
  }

  if (!skipConfirm) {
    console.log('[delete] about to run all DELETEs in a single transaction.');
    console.log('[delete] type DELETE in uppercase to proceed, anything else aborts.');
    const ans = await prompt('> ');
    if (ans.trim() !== 'DELETE') {
      console.log('[delete] aborted.');
      process.exit(0);
    }
  }

  await client.connect();
  try {
    await client.query('BEGIN');

    let totalDeleted = 0;
    for (let i = 0; i < STATEMENTS.length; i++) {
      const sql = STATEMENTS[i];
      const r = await client.query(sql);
      const n = r.rowCount || 0;
      totalDeleted += n;
      console.log(`[delete] ${String(i + 1).padStart(2)}/${STATEMENTS.length}  ${n.toString().padStart(5)}  ${sql.slice(0, 80)}`);
    }

    console.log(`[delete] ${totalDeleted} rows staged for delete. COMMIT or ROLLBACK?`);
    if (skipConfirm) {
      console.log('[delete] --yes flag set, COMMITting.');
      await client.query('COMMIT');
      console.log('[delete] COMMITTED.');
    } else {
      const ans = await prompt('> ');
      if (ans.trim().toUpperCase() === 'COMMIT') {
        await client.query('COMMIT');
        console.log('[delete] COMMITTED.');
      } else {
        await client.query('ROLLBACK');
        console.log('[delete] ROLLED BACK.');
      }
    }
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('[delete] FAILED:', e.message);
    console.error('[delete] transaction was ROLLED BACK.');
    process.exitCode = 1;
  } finally {
    await client.end();
  }
}

main().catch(err => {
  console.error('[delete] FAILED:', err.message);
  process.exit(1);
});