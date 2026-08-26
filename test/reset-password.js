const { Client } = require('pg');

async function resetPassword() {
  const client = new Client({
    host: 'localhost',
    port: 5432,
    database: 'OSMS',
    user: 'postgres',
    password: '123',
  });

  try {
    await client.connect();
    const password = '11111111';
    
    // Use PostgreSQL's crypt function to generate bcrypt hash
    const result = await client.query(
      "UPDATE users SET password_hash = crypt($1, gen_salt('bf', 10)), failed_login_attempts = 0, locked_until = NULL, status = 'ACTIVE' WHERE email IN ('admin@osms.vn', 'manager@osms.vn')",
      [password]
    );
    console.log('Reset password for ' + result.rowCount + ' account(s)');
    console.log('Password is now: ' + password);
    
    await client.end();
  } catch (err) {
    console.error('Error:', err.message);
  }
}

resetPassword();
