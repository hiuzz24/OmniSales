const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  // Same query CustomerServiceImpl uses (sumTotalSpentByCustomerId)
  const customerIds = ['391debe3-fcf5-4f3f-90f7-3599c6c60ad5', 'e1ddd468-a624-40d4-9e27-03d9567c96e6', 'e02b05c9-ceee-41f0-93bc-371ab056aea0'];

  console.log('=== Per-customer totalSpent comparison ===\n');
  for (const id of customerIds) {
    // 1) CustomerServiceImpl.sumTotalSpentByCustomerId (includes CANCELLED, NO GREATEST)
    const jpaSql = `
      SELECT COALESCE(SUM(subtotal - discount_amount + shipping_fee), 0) AS total
      FROM orders WHERE customer_id = $1
    `;
    const r1 = await c.query(jpaSql, [id]);
    const customerPageTotal = r1.rows[0].total;

    // 2) aggregateByCustomerIds (excludes CANCELLED, NO GREATEST)
    const aggregateSql = `
      SELECT COALESCE(SUM(subtotal - discount_amount + shipping_fee), 0) AS total
      FROM orders WHERE customer_id = $1 AND status <> 'CANCELLED'
    `;
    const r2 = await c.query(aggregateSql, [id]);
    const aggregateTotal = r2.rows[0].total;

    // 3) Sum of total_amount (DB-computed = GREATEST(0, ...)) excluding CANCELLED
    const dbTotalSql = `
      SELECT COALESCE(SUM(total_amount), 0) AS total
      FROM orders WHERE customer_id = $1 AND status <> 'CANCELLED'
    `;
    const r3 = await c.query(dbTotalSql, [id]);
    const orderListTotal = r3.rows[0].total;

    // 4) Sum of total_amount including CANCELLED
    const dbAllSql = `
      SELECT COALESCE(SUM(total_amount), 0) AS total
      FROM orders WHERE customer_id = $1
    `;
    const r4 = await c.query(dbAllSql, [id]);
    const orderAllTotal = r4.rows[0].total;

    console.log(`Customer ${id.substring(0,8)}:`);
    console.log(`  [Customer page] sumTotalSpentByCustomerId (incl CANCELLED, no GREATEST): ${customerPageTotal}`);
    console.log(`  [Aggregate]      aggregateByCustomerIds (excl CANCELLED, no GREATEST):    ${aggregateTotal}`);
    console.log(`  [Order list Σ]   SUM(total_amount) excl CANCELLED (DB = GREATEST):        ${orderListTotal}`);
    console.log(`  [Order list Σ]   SUM(total_amount) incl CANCELLED (DB = GREATEST):        ${orderAllTotal}`);
    console.log(`  Δ discrepancy (page - order list, excl cancelled): ${Number(customerPageTotal) - Number(orderListTotal)}`);
    console.log('');
  }

  // Detail of orders for first customer
  console.log('\n=== Detail orders for 391debe3 ===');
  const detailSql = `
    SELECT id, external_order_id, status, subtotal, discount_amount, shipping_fee,
           total_amount,
           (subtotal - discount_amount + shipping_fee) AS computed_no_greatest
    FROM orders WHERE customer_id = '391debe3-fcf5-4f3f-90f7-3599c6c60ad5'
    ORDER BY created_at
  `;
  const detail = await c.query(detailSql);
  console.log('ext_order_id | status | subtotal | discount | shipping | total_amount (DB) | computed_no_GREATEST');
  for (const o of detail.rows) {
    console.log(`${o.external_order_id} | ${o.status} | ${o.subtotal} | ${o.discount_amount} | ${o.shipping_fee} | ${o.total_amount} | ${o.computed_no_greatest}`);
  }

  await c.end();
})();