const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  // All customers with orders
  const r = await c.query(`
    SELECT c.id, c.full_name, c.is_active
    FROM customers c
    WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id)
    ORDER BY c.full_name
  `);

  console.log('Customer                       | orderCount (page) | totalSpent (page) | DB Σ totalAmount (excl CANCELLED) | Δ');
  console.log('-------------------------------|-------------------|-------------------|-----------------------------------|----');

  for (const cust of r.rows) {
    // What the API returns (now uses SUM(totalAmount) WHERE status <> 'CANCELLED')
    const api = await c.query(`
      SELECT COUNT(*) as cnt,
             COALESCE(SUM(total_amount), 0) as total
      FROM orders
      WHERE customer_id = $1 AND status <> 'CANCELLED'
    `, [cust.id]);
    const apiCnt = api.rows[0].cnt;
    const apiTotal = api.rows[0].total;

    // What the order list would show if you paginate ALL non-cancelled orders for this customer
    const orderList = await c.query(`
      SELECT id, external_order_id, status, total_amount, subtotal, discount_amount, shipping_fee
      FROM orders
      WHERE customer_id = $1 AND status <> 'CANCELLED'
      ORDER BY created_at DESC
    `, [cust.id]);
    let listTotal = 0;
    for (const o of orderList.rows) listTotal += Number(o.total_amount);

    const delta = Number(apiTotal) - listTotal;
    const match = delta === 0 ? '✅' : '❌';
    console.log(`${(cust.full_name || '').padEnd(30)} | ${String(apiCnt).padEnd(17)} | ${String(apiTotal).padEnd(17)} | ${String(listTotal).padEnd(33)} | ${delta} ${match}`);
  }

  // Detailed diff for first customer
  console.log('\n--- Detail for first customer ---');
  const cust0 = r.rows[0];
  const detail = await c.query(`
    SELECT id, external_order_id, status, total_amount
    FROM orders
    WHERE customer_id = $1
    ORDER BY created_at DESC
  `, [cust0.id]);
  console.log(`Customer: ${cust0.full_name} (${cust0.id})`);
  for (const o of detail.rows) {
    console.log(`  ${o.external_order_id} | ${o.status.padEnd(10)} | ${o.total_amount}`);
  }

  await c.end();
})();
