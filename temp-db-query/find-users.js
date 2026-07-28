const {Client} = require('pg');
(async () => {
  const c = new Client({host:'localhost',port:5432,user:'postgres',password:'123',database:'OSMS'});
  await c.connect();
  const r = await c.query("SELECT id, email, full_name FROM users LIMIT 5");
  console.log(r.rows);
  await c.end();
})();
