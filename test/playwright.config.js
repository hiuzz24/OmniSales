const { defineConfig, devices } = require('@playwright/test');
const { FRONTEND_URL: DEFAULT_FRONTEND_URL } = require('./utils/env-config');

module.exports = defineConfig({
  testDir: './',
  timeout: 30000,
  retries: 0,
  reporter: [['list']],
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
