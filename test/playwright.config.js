const { defineConfig, devices } = require('@playwright/test');
const { FRONTEND_URL: DEFAULT_FRONTEND_URL } = require('./utils/env-config');

module.exports = defineConfig({
  testDir: './',
  timeout: 90000,
  // Retries are expensive on CI (3x per failed test). With ~450 tests and 1h
  // wall-clock, even one retry loop doubles runtime. Keep 0 in CI unless
  // we know a given test is genuinely flaky.
  retries: process.env.CI ? 1 : 0,
  workers: 1, // serialize so concurrent code-generation races (receipt/delivery/transfer codes)
               // in the backend do not flake the tests; the underlying race is a separate issue.
  reporter: [
    ['list'],
    ['junit', { outputFile: 'test-results/junit.xml' }],
  ],
  use: { trace: 'on-first-retry' },
  globalSetup: require.resolve('./global-setup.js'),
  globalTeardown: require.resolve('./global-teardown.js'),
  projects: [
    {
      name: 'api',
      testMatch: /api\/.+\.spec\.js/,
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.FRONTEND_URL || DEFAULT_FRONTEND_URL,
      },
      testMatch: /e2e\/.+\.spec\.js/,
    },
  ],
});
