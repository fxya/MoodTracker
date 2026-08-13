// Caches only the static app shell (CSS/JS/icons/manifest) - never HTML
// navigations, POSTs, or /api/** - since pages carry session-bound CSRF
// tokens and per-user data that must never be served stale from cache.
//
// Bump this version string whenever a static-shell file changes: for the
// non-hashed assets below (icons/manifest/favicon), this is the only
// cache-busting signal available.
const CACHE_NAME = 'moodtracker-static-v2';

// /css/** and /js/** are deliberately NOT precached here: their URLs are now
// content-hashed by Spring's resource chain (see application.properties'
// spring.web.resources.chain.strategy.content.*), so this hand-authored,
// build-time-static file can't know the current hashed URLs ahead of time.
// The fetch handler below still lazily caches them via stale-while-revalidate
// on first request - this is a graceful downgrade from eager to lazy for
// just those two directories.
const STATIC_ASSETS = [
    '/favicon.svg',
    '/manifest.webmanifest',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS)));
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches
            .keys()
            .then((keys) =>
                Promise.all(
                    keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)),
                ),
            ),
    );
    self.clients.claim();
});

const STATIC_PATH_PREFIXES = ['/css/', '/js/', '/icons/'];
const STATIC_EXACT_PATHS = new Set([
    '/favicon.svg',
    '/manifest.webmanifest',
    '/apple-touch-icon.png',
]);

function isStaticAsset(url) {
    return (
        STATIC_EXACT_PATHS.has(url.pathname) ||
        STATIC_PATH_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))
    );
}

self.addEventListener('fetch', (event) => {
    const { request } = event;
    const url = new URL(request.url);

    if (
        request.method !== 'GET' ||
        request.mode === 'navigate' ||
        url.origin !== self.location.origin ||
        !isStaticAsset(url)
    ) {
        return;
    }

    event.respondWith(
        caches.open(CACHE_NAME).then((cache) =>
            cache.match(request).then((cached) => {
                const fetchPromise = fetch(request)
                    .then((response) => {
                        cache.put(request, response.clone());
                        return response;
                    })
                    .catch(() => cached);
                return cached || fetchPromise;
            }),
        ),
    );
});
