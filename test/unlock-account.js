const { Client } = require('pg');

// Try different common passwords
const passwords = ['postgres', 'postgres123', 'password', 'root', 'admin'];

async function tryUnlock() {
  for (const pwd of passwords) {
    const client = new Client({
      host: 'localhost',
      port: 5432,
      database: 'OSMS',
      user: 'postgres',
      password: pwd,
    });

    try {
      await client.connect();
      const result = await client.query(
        "UPDATE users SET failed_login_attempts = 0, locked_until = NULL, status = 'ACTIVE' WHERE email = 'manager@osms.vn'"
      );
      console.log(`Success! Password was: ${pwd}`);
      console.log(`Updated ${result.rowCount} row(s)`);
      await client.end();
      return;
    } catch (err) {
      console.log(`Failed with password: ${pwd} - ${err.message}`);
      try { await client.end(); } catch {}
    }
  }
  console.log('Could not connect with any common password');
}

tryUnlock();
