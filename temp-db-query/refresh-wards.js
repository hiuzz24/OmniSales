const fs = require('fs');
const path = require('path');
const {Client} = require('pg');

const FILE = path.resolve(__dirname, '..', 'backend', 'src', 'main', 'resources', 'address-data.json');
const data = JSON.parse(fs.readFileSync(FILE, 'utf8'));
const vn = data.divisions.VN;

(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  // Get existing codes from DB
  const existing = await c.query("SELECT code FROM administrative_divisions WHERE country_code='VN' AND level=3");
  const existingCodes = new Set(existing.rows.map(r => r.code));
  console.log(`Existing VN level-3 in DB: ${existingCodes.size}`);

  // Insert new wards
  const newWards = vn.filter(x => x.level === 3 && !existingCodes.has(x.code));
  console.log(`New wards to insert: ${newWards.length}`);

  let inserted = 0;
  let skipped = 0;
  const BATCH = 200;
  for (let i = 0; i < newWards.length; i += BATCH) {
    const batch = newWards.slice(i, i + BATCH);
    // Filter batch against existingCodes
    const filtered = batch.filter(w => !existingCodes.has(w.code));
    skipped += batch.length - filtered.length;
    if (filtered.length === 0) continue;

    const values = [];
    const placeholders = [];
    let pi = 1;
    for (const w of filtered) {
      values.push('VN', w.code, w.name, 3, w.parentCode);
      placeholders.push(`($${pi++},$${pi++},$${pi++},$${pi++},$${pi++})`);
    }
    const sql = `INSERT INTO administrative_divisions (country_code, code, name, level, parent_code) VALUES ${placeholders.join(',')}`;
    const res = await c.query(sql, values);
    inserted += res.rowCount;
    // Update existingCodes to prevent duplicate within batch
    filtered.forEach(w => existingCodes.add(w.code));
    process.stdout.write(`\r  inserted ${inserted}/${newWards.length} (skipped ${skipped})`);
  }
  console.log(`\nDone. Total inserted: ${inserted}, skipped: ${skipped}`);

  const total = await c.query("SELECT count(*) AS n FROM administrative_divisions WHERE country_code='VN' AND level=3");
  console.log(`Total VN level-3 in DB now: ${total.rows[0].n}`);

  await c.end();
})().catch(e => { console.error(e.message); process.exit(1); });
