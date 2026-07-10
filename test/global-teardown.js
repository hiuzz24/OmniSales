/**
 * Global teardown - runs once after all tests have completed.
 * Kept as a no-op for now; placeholder for future cleanup tasks
 * (e.g. truncating test rows, stopping docker containers).
 */

module.exports = async () => {
  console.log('[teardown] test run finished at', new Date().toISOString());
};
