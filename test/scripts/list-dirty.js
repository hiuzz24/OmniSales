const pg = require('pg');
const c = new pg.Client({ connectionString: 'postgresql://postgres:123@localhost:5432/OSMS' });

(async () => {
  try {
    await c.connect();
    const r = await c.query(
      `SELECT id, display_name FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%' OR display_name LIKE 'Updated Manual Channel %'`
    );
    console.log('Dirty channels:', r.rows.length);
    r.rows.forEach(r => console.log(`  ${r.id}: ${r.display_name}`));
    await c.end();
  } catch (e) {
    console.error('Error:', e.message);
    process.exit(1);
  }
})();