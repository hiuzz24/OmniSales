const {Client} = require('pg');
const crypto = require('crypto');
const bcrypt = require('bcrypt');

(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();

  // Test password against hash
  const r = await c.query("SELECT email, password_hash FROM users WHERE email IN ('manager@osms.vn', 'staff@osms.vn', 'viewer@osms.vn', 'duy@gmail.com', 'duy1@gmail.com', 'duynguyenthe195@gmail.com')");
  for (const u of r.rows) {
    const valid111 = await bcrypt.compare('11111111', u.password_hash);
    const valid123 = await bcrypt.compare('123456', u.password_hash);
    const valid1234 = await bcrypt.compare('12345678', u.password_hash);
    const validAdmin = await bcrypt.compare('Admin@123', u.password_hash);
    console.log(u.email, '| 11111111:', valid111, '| 123456:', valid123, '| 12345678:', valid1234, '| Admin@123:', validAdmin);
  }
  await c.end();
})();
