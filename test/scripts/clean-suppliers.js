const pg = require('pg');
const c = new pg.Client({ connectionString: 'postgresql://postgres:123@localhost:5432/OSMS' });

(async () => {
  try {
    await c.connect();
    const r = await c.query(`UPDATE suppliers SET is_active=false WHERE name LIKE 'TestSup%' OR name LIKE 'ToUpdate%' OR name LIKE 'StatusTest%' OR name LIKE 'DuplicateTest%' OR name LIKE 'Some Supplier %' OR email LIKE 'supplier%@example.com'`);
    console.log('Updated:', r.rowCount);
    await c.end();
  } catch (e) {
    console.error('Error:', e.message);
    process.exit(1);
  }
})();