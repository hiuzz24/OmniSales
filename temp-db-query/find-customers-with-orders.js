const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();
  // Find a customer with orders
  const r = await c.query(`
    SELECT c.id, c.full_name, count(o.id) as order_count
    FROM customers c
    JOIN orders o ON o.customer_id = c.id
    GROUP BY c.id, c.full_name
    HAVING count(o.id) > 0
    ORDER BY order_count DESC
    LIMIT 3
  `);
  console.log('Top 3 customers with orders:');
  for (const x of r.rows) console.log(' ', x.id, '|', x.full_name, '|', x.order_count, 'orders');
  await c.end();
})();
