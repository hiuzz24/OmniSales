const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();
  const r = await c.query("SELECT email, status FROM users WHERE status='ACTIVE' LIMIT 10");
  console.log(r.rows);
  await c.end();
})();
