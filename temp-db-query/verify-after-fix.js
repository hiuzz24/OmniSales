const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  // Reference query: SUM(total_amount) WHERE customer_id = X AND status <> 'CANCELLED'
  const ref = await c.query(`
    SELECT c.id, c.full_name,
           COUNT(o.id) FILTER (WHERE o.status <> 'CANCELLED') AS ref_count,
           COALESCE(SUM(o.total_amount) FILTER (WHERE o.status <> 'CANCELLED'), 0) AS ref_total
    FROM customers c
    LEFT JOIN orders o ON o.customer_id = c.id
    GROUP BY c.id, c.full_name
    ORDER BY c.full_name
  `);
  console.log('Customer                    | API count | API total          | DB count | DB total          | Match?');
  console.log('----------------------------|-----------|---------------------|----------|-------------------|-------');
  for (const r of ref.rows) {
    console.log(`${(r.full_name || '').padEnd(28)}| (see API) | (see API)          | ${String(r.ref_count).padEnd(8)} | ${String(r.ref_total).padEnd(17)}  |`);
  }

  console.log('\n--- Detail check for "Khách vãng lai" (e1ddd468) ---');
  const detail = await c.query(`
    SELECT id, external_order_id, status, subtotal, discount_amount, shipping_fee, total_amount
    FROM orders WHERE customer_id = 'e1ddd468-a624-40d4-9e27-03d9567c96e6'
    ORDER BY created_at
  `);
  let nonCancelledTotal = 0;
  console.log('ext_order_id | status | total_amount');
  for (const o of detail.rows) {
    console.log(`  ${o.external_order_id} | ${o.status.padEnd(10)} | ${o.total_amount}`);
    if (o.status !== 'CANCELLED') nonCancelledTotal += Number(o.total_amount);
  }
  console.log(`\nManual sum (excl CANCELLED): ${nonCancelledTotal}`);

  await c.end();
})();
