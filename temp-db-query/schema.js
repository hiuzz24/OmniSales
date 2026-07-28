const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();
  const cols = await c.query("SELECT column_name, data_type FROM information_schema.columns WHERE table_name='administrative_divisions' ORDER BY ordinal_position");
  console.log('Columns:', cols.rows);
  const idx = await c.query("SELECT indexname, indexdef FROM pg_indexes WHERE tablename='administrative_divisions'");
  console.log('Indexes:', idx.rows);
  await c.end();
})();
