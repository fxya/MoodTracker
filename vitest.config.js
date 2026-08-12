import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        // Only scan actual source - Gradle copies static/js/**/*.test.mjs into
        // build/resources/main as part of its normal resource processing, and
        // without this exclusion Vitest picks up that copy too.
        exclude: ['**/node_modules/**', '**/build/**']
    }
});
