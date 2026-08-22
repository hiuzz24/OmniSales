const { Client } = require('pg');

async function unlock() {
  const client = new Client({
    host: 'localhost',
    port: 5432,
    database: 'OSMS',
    user: 'postgres',
    password: '123',
  });

  try {
    await client.connect();
    const result = await client.query(
      "UPDATE users SET failed_login_attempts = 0, locked_until = NULL, status = 'ACTIVE' WHERE email IN ('admin@osms.vn', 'manager@osms.vn')"
    );
    console.log('Unlocked ' + result.rowCount + ' account(s)');

    // Also find the correct password
    const users = await client.query("SELECT email, password_hash FROM users WHERE email IN ('admin@osms.vn', 'manager@osms.vn')");
    console.log('\nUser passwords from DB:');
    users.rows.forEach(u => console.log(u.email + ': ' + u.password_hash));

    await client.end();
  } catch (err) {
    console.error('Error:', err.message);
  }
}

unlock();
