const pg = require('pg');
const c = new pg.Client({ connectionString: 'postgresql://postgres:123@localhost:5432/OSMS' });

(async () => {
  try {
    await c.connect();
    const tables = ['products','product_variants','product_images','product_logs','customers','categories','users','user_invite_tokens','channels','channel_products','channel_credentials','channel_connection_logs','suppliers','stocktake_sessions','stocktake_items','stock_transfers','stock_transfer_items','orders','order_items','audit_logs','notifications','inventory_transactions','addresses'];
    for (const t of tables) {
      const r = await c.query(`SELECT to_regclass($1) AS exists`, [t]);
      console.log(`${t.padEnd(28)} ${r.rows[0].exists ? 'EXISTS' : 'MISSING'}`);
    }
    console.log('---');
    const supCols = await c.query(`SELECT column_name FROM information_schema.columns WHERE table_name='suppliers'`);
    console.log('suppliers cols:', supCols.rows.map(r => r.column_name).join(','));
    const notiCols = await c.query(`SELECT column_name FROM information_schema.columns WHERE table_name='notifications'`);
    console.log('notifications cols:', notiCols.rows.map(r => r.column_name).join(','));
    const ordCols = await c.query(`SELECT column_name FROM information_schema.columns WHERE table_name='orders'`);
    console.log('orders cols:', ordCols.rows.map(r => r.column_name).join(','));
    await c.end();
  } catch (e) {
    console.error('Error:', e.message);
    process.exit(1);
  }
})();