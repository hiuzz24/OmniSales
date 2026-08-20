const { defineConfig, devices } = require('@playwright/test');
const { FRONTEND_URL: DEFAULT_FRONTEND_URL } = require('./utils/env-config');

module.exports = defineConfig({
  testDir: './',
  timeout: 90000,
  retries: process.env.CI ? 2 : 0,
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
