const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  const r1 = await c.query('SELECT id, full_name, phone, created_at FROM customers ORDER BY created_at');
  console.log('All customers:');
  for (const x of r1.rows) console.log(' ', x.id.substring(0,8), '|', x.full_name, '|', x.phone, '|', x.created_at);

  const r2 = await c.query(`SELECT count(*) FILTER (WHERE customer_id IS NOT NULL) AS linked, count(*) AS total, count(DISTINCT customer_id) AS distinct_cust FROM orders`);
  console.log('orders stats:', r2.rows[0]);

  const r3 = await c.query(`SELECT c.full_name, c.phone, count(o.id) AS order_count, sum(o.total_amount) AS spent FROM customers c LEFT JOIN orders o ON o.customer_id = c.id GROUP BY c.id, c.full_name, c.phone ORDER BY order_count DESC`);
  console.log('customer -> orders:');
  for (const x of r3.rows) console.log(' ', x.full_name, '|', x.phone, '| orders=', x.order_count, '| spent=', x.spent);

  await c.end();
})().catch(e => { console.error(e.message); process.exit(1); });
