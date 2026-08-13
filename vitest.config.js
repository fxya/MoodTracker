import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        // e2e/**: Playwright specs, run via `npm run test:e2e` (playwright test),
        // not Vitest - they'd otherwise match Vitest's default *.spec.js glob too.
        exclude: ['**/node_modules/**', '**/build/**', 'e2e/**']
    }
});
