const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  for (const provinceName of ['Hà Nội', 'Hồ Chí Minh', 'Đà Nẵng', 'Bắc Ninh']) {
    const p = await c.query("SELECT code, name FROM administrative_divisions WHERE country_code='VN' AND level=1 AND name LIKE $1", [`%${provinceName}%`]);
    if (p.rows.length === 0) { console.log('NOT FOUND:', provinceName); continue; }
    const province = p.rows[0];
    const districts = await c.query("SELECT code, name FROM administrative_divisions WHERE country_code='VN' AND level=2 AND parent_code=$1", [province.code]);
    const wards = await c.query("SELECT count(*)::int AS n FROM administrative_divisions WHERE country_code='VN' AND level=3 AND parent_code LIKE $1", [province.code + '|%']);

    console.log(`\n${province.name} (${province.code}): ${districts.rows.length} districts, ${wards.rows[0].n} wards`);
    let withZero = 0;
    for (const d of districts.rows) {
      const wn = await c.query("SELECT count(*)::int AS n FROM administrative_divisions WHERE country_code='VN' AND level=3 AND parent_code=$1", [d.code]);
      const n = wn.rows[0].n;
      if (n === 0) { withZero++; console.log(`  ⚠ ${d.name}: 0 wards`); }
    }
    if (withZero === 0) console.log(`  ✓ Tất cả ${districts.rows.length} quận/huyện đều có phường/xã.`);
  }

  await c.end();
})().catch(e => { console.error(e.message); process.exit(1); });
