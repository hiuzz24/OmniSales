/**
 * Global setup - runs once before all tests in a Playwright run.
 *
 * Verifies that backend and frontend are reachable. Fails fast with
 * a clear message when the user forgot to start the dev servers.
 *
 * Uses Node's built-in `http` module so we do not depend on
 * Playwright APIs (globalSetup runs outside the test context).
 */

const http = require('node:http');

function checkUrl(url, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, { timeout: timeoutMs }, (res) => {
      res.resume(); // drain to free memory
      resolve({ url, status: res.statusCode });
    });
    req.on('timeout', () => {
      req.destroy(new Error(`Timeout after ${timeoutMs}ms: ${url}`));
    });
    req.on('error', (err) => reject(err));
  });
}

module.exports = async () => {
  const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5174';
  const API_BASE = process.env.API_BASE || 'http://localhost:8080/api';
  // Backend has no dedicated /health endpoint; use the public
  // /address/countries endpoint as a liveness proxy.
  const BACKEND_HEALTH = `${API_BASE}/address/countries`;

  console.log('[setup] verifying frontend:', FRONTEND_URL);
  try {
    const fe = await checkUrl(FRONTEND_URL);
    if (fe.status >= 500) {
      throw new Error(`Frontend unhealthy (status ${fe.status}): ${FRONTEND_URL}`);
    }
    console.log(`[setup] frontend OK (status ${fe.status})`);
  } catch (err) {
    throw new Error(
      `Cannot reach frontend at ${FRONTEND_URL}. ` +
      `Start the Vite dev server (npm run dev) before running tests. ` +
      `Original error: ${err.message}`,
    );
  }

  console.log('[setup] verifying backend via', BACKEND_HEALTH);
  try {
    const be = await checkUrl(BACKEND_HEALTH);
    if (be.status >= 500) {
      throw new Error(`Backend unhealthy (status ${be.status}): ${BACKEND_HEALTH}`);
    }
    console.log(`[setup] backend OK (status ${be.status})`);
  } catch (err) {
    throw new Error(
      `Cannot reach backend at ${BACKEND_HEALTH}. ` +
      `Start the Spring Boot app before running tests. ` +
      `Original error: ${err.message}`,
    );
  }

  console.log('[setup] all systems ready.');
};