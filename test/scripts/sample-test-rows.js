const { Client } = require('pg');
(async () => {
  const c = new Client({ host: 'localhost', port: 5432, user: 'postgres', password: '123', database: 'OSMS' });
  await c.connect();
  // Check immutability triggers
  const r = await c.query(`
    SELECT tgrelid::regclass AS table_name, tgname, tgenabled
    FROM pg_trigger
    WHERE NOT tgisinternal
      AND (tgname LIKE '%immutable%' OR tgname = 'trg_orders_before_update')
    ORDER BY table_name
  `);
  for (const row of r.rows) console.log(`  ${row.table_name} | ${row.tgname} | enabled=${row.tgenabled}`);

  // Try inserting a fake immutability test
  console.log('--- trying to INSERT into inventory_transactions');
  try {
    await c.query(`INSERT INTO inventory_transactions (warehouse_id, variant_id, type, quantity_change, quantity_before, quantity_after, note) VALUES ((SELECT id FROM warehouses LIMIT 1), (SELECT id FROM product_variants LIMIT 1), 'ADJUSTMENT', 1, 0, 1, 'test')`);
    console.log('  insert OK (BAD — trigger should have blocked this for some tables)');
    await c.query(`DELETE FROM inventory_transactions WHERE note='test'`);
  } catch (e) {
    console.log(`  insert failed (expected): ${e.message}`);
  }

  console.log('--- trying to DELETE from inventory_transactions');
  try {
    const r2 = await c.query(`SELECT count(*) FROM inventory_transactions`);
    console.log('  before:', r2.rows[0].count);
    if (parseInt(r2.rows[0].count) > 0) {
      await c.query(`DELETE FROM inventory_transactions LIMIT 1`);
      console.log('  delete SUCCEEDED — trigger was NOT re-enabled');
    } else {
      console.log('  empty, cannot test');
    }
  } catch (e) {
    console.log(`  delete blocked (expected): ${e.message.slice(0, 100)}`);
  }

  await c.end();
})().catch(e => { console.error(e); process.exit(1); });