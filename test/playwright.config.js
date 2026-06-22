const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './',
  timeout: 30000,
  retries: 0,
  reporter: [['list']],
  use: { trace: 'on-first-retry' },
  projects: [
    {
      name: 'api',
      testMatch: /api\/.+\.spec\.js/,
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.FRONTEND_URL || 'http://localhost:5174',
      },
      testMatch: /e2e\/.+\.spec\.js/,
    },
  ],
});
