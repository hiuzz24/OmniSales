const pg = require('pg');
const c = new pg.Client({ connectionString: 'postgresql://postgres:123@localhost:5432/OSMS' });

(async () => {
  try {
    await c.connect();
    const queries = [
      ["Test channels", `SELECT COUNT(*) FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%' OR display_name LIKE 'Updated Manual Channel %'`],
      ["Test products", `SELECT COUNT(*) FROM products WHERE sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%' OR sku LIKE 'VAR-%' OR sku LIKE 'DUP-%'`],
      ["Test customers", `SELECT COUNT(*) FROM customers WHERE full_name LIKE 'Test Customer %'`],
      ["Test categories", `SELECT COUNT(*) FROM categories WHERE name LIKE 'Test Category %' OR name LIKE 'API Test %' OR slug LIKE 'test-category-%'`],
      ["Test users", `SELECT COUNT(*) FROM users WHERE email LIKE 'testuser_%' OR email LIKE 'newuser_%' OR email LIKE 'invitee+%@osms-test.vn'`],
      ["Test stocktakes", `SELECT COUNT(*) FROM stocktake_sessions WHERE session_code LIKE 'KK-%'`],
      ["Test transfers", `SELECT COUNT(*) FROM stock_transfers WHERE transfer_code LIKE 'CK-%'`],
      ["Test suppliers", `SELECT COUNT(*) FROM suppliers WHERE (name LIKE 'TestSup%' OR name LIKE 'ToUpdate%' OR name LIKE 'StatusTest%' OR name LIKE 'DuplicateTest%' OR name LIKE 'Some Supplier %' OR email LIKE 'supplier%@example.com') AND is_active = true`],
      ["Test invite tokens", `SELECT COUNT(*) FROM user_invite_tokens WHERE email LIKE 'invitee+%@osms-test.vn'`],
      ["Test inventory TX", `SELECT COUNT(*) FROM inventory_transactions WHERE note LIKE 'Test transaction %'`],
    ];
    for (const [label, sql] of queries) {
      const r = await c.query(sql);
      const count = r.rows[0].count;
      const status = count === '0' ? 'OK' : 'DIRTY';
      console.log(`${status.padEnd(6)} | ${label.padEnd(22)} | ${count}`);
    }
    await c.end();
  } catch (e) {
    console.error('Error:', e.message);
    process.exit(1);
  }
})();