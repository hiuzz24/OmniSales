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

const TEST_EMAIL = readEnv('TEST_EMAIL', 'manager@osms.vn');
const TEST_PASSWORD = readEnv('TEST_PASSWORD', 'Duy16042004%');
const API_BASE = readEnv('API_BASE', 'http://localhost:8080/api');
const FRONTEND_URL = readEnv('FRONTEND_URL', 'http://localhost:5174');

module.exports = {
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
  FRONTEND_URL,
};