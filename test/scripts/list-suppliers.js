const pg = require('pg');
const c = new pg.Client({ connectionString: 'postgresql://postgres:123@localhost:5432/OSMS' });

(async () => {
  try {
    await c.connect();
    const r = await c.query(`SELECT id, name, email, is_active FROM suppliers WHERE name LIKE 'TestSup%' OR name LIKE 'ToUpdate%' OR name LIKE 'StatusTest%' OR name LIKE 'DuplicateTest%' OR name LIKE 'Some Supplier %' OR email LIKE 'supplier%@example.com'`);
    console.log('Test suppliers:', r.rows.length);
    r.rows.forEach(r => console.log(`  ${r.id} | ${r.name} | ${r.email} | active=${r.is_active}`));
    await c.end();
  } catch (e) {
    console.error('Error:', e.message);
    process.exit(1);
  }
})();