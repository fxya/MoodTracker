import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';

// Not a SPA: this app is server-rendered Thymeleaf, and Vite owns no HTML
// entry point. Instead it's driven purely by rollupOptions.input (one CSS
// entry, one JS entry) and configured to emit fixed, non-hashed filenames
// straight into the paths the templates already reference - no manifest
// lookup needed. Hashed/cache-busted output is a good future improvement,
// deliberately deferred: this build step's only job right now is to prove
// the pipeline compiles to byte-identical behavior.
export default defineConfig({
    plugins: [tailwindcss()],
    // manifest.webmanifest, service-worker.js, and the PWA icons live here and
    // are copied byte-for-byte into outDir - unlike rollupOptions.input, this
    // bypasses Rollup's ESM bundling, which matters for service-worker.js:
    // it must stay a plain classic script with no import/export syntax.
    publicDir: 'frontend/public',
    build: {
        outDir: 'src/main/resources/static',
        // Default true would wipe the hand-authored favicon.svg that also
        // lives under static/ - this directory isn't Vite's alone.
        emptyOutDir: false,
        rollupOptions: {
            input: {
                app: 'frontend/css/app.css',
                moods: 'frontend/js/moods.js',
                'register-sw': 'frontend/js/register-sw.js',
            },
            output: {
                entryFileNames: 'js/[name].js',
                assetFileNames: (assetInfo) =>
                    assetInfo.name === 'app.css' ? 'css/style.css' : 'assets/[name][extname]',
            },
        },
    },
});
