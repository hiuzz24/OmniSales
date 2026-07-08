/**
 * Admin auth helpers for OmniSales API + E2E tests.
 *
 * Backend gates admin endpoints with @PreAuthorize("hasRole('SYSTEM_ADMIN')").
 * Seed user (from backend/data/init or hibernate-schema.sql):
 *   email:    admin@osms.vn
 *   password: 11111111
 *
 * Use ADMIN_EMAIL / ADMIN_PASSWORD env vars to override the defaults.
 *
 * Usage in API specs:
 *   const { getAdminAuthHeaders } = require('../../utils/admin-helpers');
 *   test.beforeAll(async ({ request }) => {
 *     adminHeaders = await getAdminAuthHeaders(request);
 *   });
 */

const {
  API_BASE: ENV_API_BASE,
} = require('./env-config');
const API_BASE = process.env.API_BASE || ENV_API_BASE;

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@osms.vn';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '11111111';

async function getAdminAuthToken(request) {
  const response = await request.post(`${API_BASE}/auth/login`, {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });

  if (response.status() !== 200) {
    throw new Error(
      `Admin login failed: status=${response.status()} (${ADMIN_EMAIL}). ` +
      `Set ADMIN_EMAIL / ADMIN_PASSWORD env vars if your seed data differs.`
    );
  }

  const body = await response.json();
  if (!body.data || !body.data.accessToken) {
    throw new Error(`Admin login response missing accessToken: ${JSON.stringify(body)}`);
  }
  return body.data.accessToken;
}

async function getAdminAuthHeaders(request) {
  const token = await getAdminAuthToken(request);
  return { Authorization: `Bearer ${token}` };
}

module.exports = {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  API_BASE,
  getAdminAuthToken,
  getAdminAuthHeaders,
};
