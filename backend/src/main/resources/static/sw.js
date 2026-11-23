/* NutriAI PWA service worker — app shell cache + offline fallback */
const VERSION = 'nutriai-v20261123-3';
const SHELL = [
  '/',
  '/index.html',
  '/offline.html',
  '/css/style.css',
  '/css/assistant.css',
  '/css/editorial.css',
  '/js/app.js',
  '/js/assistant.js',
  '/vendor/chart.umd.min.js',
  '/vendor/marked.min.js',
  '/favicon.svg',
  '/icons/icon-192.png',
  '/icons/icon-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(VERSION)
      .then((cache) => cache.addAll(SHELL).catch(() => undefined))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  // 用户数据/接口永远走网络，不缓存隐私数据
  if (url.pathname.startsWith('/api/')) return;
  // manifest 必须始终走网络，确保返回正确的 application/manifest+json，否则移动端浏览器会忽略 manifest、无法触发安装
  if (url.pathname === '/manifest.webmanifest') return;

  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(VERSION).then((c) => c.put('/index.html', copy)).catch(() => undefined);
          return res;
        })
        .catch(() => caches.match('/index.html').then((hit) => hit || caches.match('/offline.html')))
    );
    return;
  }

  event.respondWith(
    caches.match(req).then((hit) => {
      if (hit) {
        fetch(req).then((fresh) => {
          if (fresh && fresh.ok) caches.open(VERSION).then((c) => c.put(req, fresh)).catch(() => undefined);
        }).catch(() => undefined);
        return hit;
      }
      return fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(VERSION).then((c) => c.put(req, copy)).catch(() => undefined);
          }
          return res;
        })
        .catch(() => caches.match('/offline.html'));
    })
  );
});