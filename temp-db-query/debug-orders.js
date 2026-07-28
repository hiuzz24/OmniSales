const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();
  const r = await c.query(`SELECT id, external_order_id, status, subtotal, total_amount, created_at
    FROM orders
    WHERE external_order_id LIKE 'DBG-%' OR external_order_id LIKE 'REG-%'
    ORDER BY created_at DESC LIMIT 10`);
  console.log('Total matching:', r.rows.length);
  for (const o of r.rows) console.log(o.external_order_id, '|', o.status, '|', o.total_amount, '|', o.created_at);
  await c.end();
})();
