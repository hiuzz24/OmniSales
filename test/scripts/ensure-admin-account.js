/**
 * test/scripts/ensure-admin-account.js
 *
 * Ensures the SYSTEM_ADMIN user (admin@osms.vn) and its SYSTEM_ADMIN role
 * are present in the database. Safe to run repeatedly (idempotent).
 *
 * The schema in backend/hibernate-schema.sql already inserts this user, but
 * on databases that were seeded before the admin role existed, or after a
 * partial migration, this script makes the tests resilient.
 *
 * Usage:
 *   node scripts/ensure-admin-account.js
 */

const path = require('path');
const pg = require('pg');

require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
require('dotenv').config({ path: path.join(__dirname, '..', '..', 'backend', '.env') });

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@osms.vn';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '11111111';
const ADMIN_FULL_NAME = 'Admin';
const ADMIN_PHONE = '0901000001';
const ADMIN_ROLE = 'SYSTEM_ADMIN';

const DB_PASSWORD = process.env.DB_PASSWORD || '123';
const DATABASE_URL =
  process.env.DATABASE_URL ||
  `postgresql://${process.env.DB_USERNAME || 'postgres'}:${DB_PASSWORD}@${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}/${process.env.DB_NAME || 'OSMS'}`;

async function ensureRole(client, roleName) {
  const r = await client.query('SELECT id FROM roles WHERE name = $1', [roleName]);
  if (r.rowCount > 0) {
    console.log(`[ensure-admin] role "${roleName}" exists (id=${r.rows[0].id})`);
    return r.rows[0].id;
  }
  const ins = await client.query(
    'INSERT INTO roles (name, description) VALUES ($1, $2) RETURNING id',
    [roleName, 'System administrator'],
  );
  console.log(`[ensure-admin] created role "${roleName}" (id=${ins.rows[0].id})`);
  return ins.rows[0].id;
}

async function ensureUser(client) {
  const r = await client.query(
    'SELECT id, status FROM users WHERE email = $1',
    [ADMIN_EMAIL],
  );
  if (r.rowCount > 0) {
    const userId = r.rows[0].id;
    console.log(`[ensure-admin] user "${ADMIN_EMAIL}" exists (id=${userId}, status=${r.rows[0].status})`);
    if (r.rows[0].status !== 'ACTIVE') {
      await client.query(
        "UPDATE users SET status = 'ACTIVE' WHERE id = $1",
        [userId],
      );
      console.log(`[ensure-admin] reactivated user "${ADMIN_EMAIL}"`);
    }
    // Also make sure password matches the expected one in case it was changed.
    await client.query(
      `UPDATE users SET password_hash = crypt($1, gen_salt('bf', 10))
       WHERE id = $2 AND password_hash <> crypt($1, gen_salt('bf', 10))`,
      [ADMIN_PASSWORD, userId],
    );
    return userId;
  }
  // bcrypt hash via pgcrypto, same as hibernate-schema.sql seed.
  const ins = await client.query(
    `INSERT INTO users (id, email, password_hash, full_name, phone, status)
     VALUES (gen_random_uuid(), $1, crypt($2, gen_salt('bf', 10)), $3, $4, 'ACTIVE')
     RETURNING id`,
    [ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_FULL_NAME, ADMIN_PHONE],
  );
  console.log(`[ensure-admin] created user "${ADMIN_EMAIL}" (id=${ins.rows[0].id})`);
  return ins.rows[0].id;
}

async function ensureUserRole(client, userId, roleId) {
  const r = await client.query(
    'SELECT id FROM user_roles WHERE user_id = $1 AND role_id = $2',
    [userId, roleId],
  );
  if (r.rowCount > 0) {
    console.log(`[ensure-admin] user-role mapping already exists`);
    return;
  }
  await client.query(
    'INSERT INTO user_roles (id, user_id, role_id) VALUES (gen_random_uuid(), $1, $2)',
    [userId, roleId],
  );
  console.log(`[ensure-admin] assigned role "${ADMIN_ROLE}" to "${ADMIN_EMAIL}"`);
}

async function main() {
  const dbConfig = {
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USERNAME || 'postgres',
    password: DB_PASSWORD,
    database: process.env.DB_NAME || 'OSMS',
  };
  console.log(`[ensure-admin] DB_PASSWORD length = ${DB_PASSWORD.length} (first char: '${DB_PASSWORD[0]}')`);
  console.log(`[ensure-admin] connecting to ${dbConfig.user}@${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`);
  const client = new pg.Client(dbConfig);
  await client.connect();
  try {
    const roleId = await ensureRole(client, ADMIN_ROLE);
    const userId = await ensureUser(client);
    await ensureUserRole(client, userId, roleId);
    console.log('[ensure-admin] DONE — admin account is ready.');
  } finally {
    await client.end();
  }
}

main().catch((e) => {
  console.error('[ensure-admin] FAILED:', e.message);
  process.exit(1);
});