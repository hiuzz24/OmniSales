const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  // Insert a new order WITHOUT customer_id, with buyer_name/buyer_phone
  const newOrder = await c.query(
    `INSERT INTO orders (id, channel_name, platform, external_order_id, buyer_name, buyer_phone, shipping_address, status, payment_status, subtotal, discount_amount, shipping_fee, currency, version, created_at, updated_at)
     VALUES (gen_random_uuid(), 'Test Channel', 'TIKTOK', 'TEST-001-' || extract(epoch from now())::int,
             'Test KH Tu Dong', '+84909000999',
             '{"detail":"123 Lý Tự Trọng"}'::jsonb,
             'PENDING', 'UNPAID', 100000, 0, 30000, 'VND', 0, now(), now())
     RETURNING id, buyer_name, customer_id`
  );
  console.log('Inserted test order (no customer_id):');
  console.log(' ', newOrder.rows[0].id, '|', newOrder.rows[0].buyer_name, '| customer_id=', newOrder.rows[0].customer_id);
  const orderId = newOrder.rows[0].id;

  const nullCount = await c.query('SELECT count(*) AS n FROM orders WHERE customer_id IS NULL');
  console.log('Orders with null customer_id:', nullCount.rows[0].n);

  // Cleanup
  await c.query('DELETE FROM orders WHERE id = $1', [orderId]);
  console.log('Cleaned up test data.');

  await c.end();
})().catch(e => { console.error(e.message); process.exit(1); });
