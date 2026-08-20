/**
 * Central configuration loader for Playwright tests.
 *
 * Reads credentials and base URLs from `.env` (or process.env which
 * takes precedence so CI / pipelines can inject values without
 * shipping the `.env` file).
 *
 * Usage:
 *   const { TEST_EMAIL, TEST_PASSWORD, API_BASE } = require('./env-config');
 */

const path = require('path');

// Load .env from the `test/` folder regardless of where Playwright
// is invoked from.
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

function readEnv(key, fallback) {
  const value = process.env[key];
  if (value === undefined || value === '') return fallback;
  return value;
}

// Passwords are seeded by backend/src/main/resources/schema.sql with
// crypt('11111111', gen_salt('bf', 10)) for every demo account. Keep
// these aligned with the DB seed so the warmup + fixtures can actually
// log in. Override via env vars when running against a different DB.
const TEST_EMAIL = readEnv('TEST_EMAIL', 'manager@osms.vn');
const TEST_PASSWORD = readEnv('TEST_PASSWORD', '11111111');
const API_BASE = readEnv('API_BASE', 'http://localhost:8080/api');
const FRONTEND_URL = readEnv('FRONTEND_URL', 'http://localhost:5174');
const ADMIN_EMAIL = readEnv('ADMIN_EMAIL', 'admin@osms.vn');
const ADMIN_PASSWORD = readEnv('ADMIN_PASSWORD', '11111111');

module.exports = {
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
  FRONTEND_URL,
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
};