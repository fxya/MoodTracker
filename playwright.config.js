import { defineConfig, devices } from '@playwright/test';

// Postgres must already be running before these tests start (locally: the
// dev machine's own instance; in CI: the e2e job's service container - same
// pattern the Java test suite already relies on). webServer only manages the
// app process itself.
export default defineConfig({
    testDir: './e2e',
    fullyParallel: true,
    reporter: 'html',
    use: {
        baseURL: 'http://localhost:8080',
        trace: 'on-first-retry',
    },
    webServer: {
        command: './gradlew bootRun',
        url: 'http://localhost:8080/login',
        // Reuse an already-running local bootRun (common during manual
        // verification) instead of starting a duplicate; CI always starts fresh.
        reuseExistingServer: !process.env.CI,
        timeout: 180_000,
    },
    projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
