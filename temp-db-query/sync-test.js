const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  // Insert a new order WITHOUT customer_id, with buyer_name/buyer_phone
  const newOrder = await c.query(
    `INSERT INTO orders (id, channel_name, platform, external_order_id, buyer_name, buyer_phone, shipping_address, status, payment_status, subtotal, discount_amount, shipping_fee, total_amount, currency, version, created_at, updated_at)
     VALUES (gen_random_uuid(), 'Test Channel', 'TIKTOK', 'TEST-001-' || extract(epoch from now())::int,
             'Test KH Tu Dong', '+84909000999',
             '{"detail":"123 Lý Tự Trọng"}'::jsonb,
             'PENDING', 'UNPAID', 100000, 0, 30000, 0, 'VND', 0, now(), now())
     RETURNING id, buyer_name, customer_id`
  );
  console.log('Inserted order:');
  console.log(' ', newOrder.rows[0].id, '|', newOrder.rows[0].buyer_name, '| customer_id=', newOrder.rows[0].customer_id);

  // Check customer count before sync
  const before = await c.query('SELECT count(*) AS n FROM customers');
  console.log('Customers before sync:', before.rows[0].n);

  // Call sync endpoint
  const http = require('http');
  const opts = {hostname:'localhost',port:8080,path:'/api/customers/sync-from-orders',method:'POST'};
  const req = http.request(opts, res => {
    let body = '';
    res.on('data', c => body += c);
    res.on('end', async () => {
      console.log('Sync response:', res.statusCode, body);

      const after = await c.query('SELECT count(*) AS n FROM customers');
      console.log('Customers after sync:', after.rows[0].n);

      const updated = await c.query('SELECT id, buyer_name, customer_id FROM orders WHERE buyer_name = $1', ['Test KH Tu Dong']);
      console.log('Updated test order:');
      console.log(' ', updated.rows[0]?.id, '|', updated.rows[0]?.buyer_name, '| customer_id=', updated.rows[0]?.customer_id);

      // Find newly created customer
      const newCust = await c.query("SELECT id, full_name, phone FROM customers WHERE full_name = 'Test KH Tu Dong'");
      console.log('New customer:');
      console.log(' ', newCust.rows[0]?.id, '|', newCust.rows[0]?.full_name, '|', newCust.rows[0]?.phone);

      // Cleanup
      if (updated.rows[0]?.customer_id) {
        await c.query('DELETE FROM orders WHERE id = $1', [updated.rows[0].id]);
        await c.query('DELETE FROM customers WHERE id = $1 AND full_name = $2', [updated.rows[0].customer_id, 'Test KH Tu Dong']);
        console.log('Cleaned up test data.');
      }
      await c.end();
    });
  });
  req.end();
})().catch(e => { console.error(e.message); process.exit(1); });
