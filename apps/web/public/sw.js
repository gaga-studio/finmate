const CACHE_NAME = 'finmate-shell-v1'
const SHELL_ASSETS = [
  '/',
  '/login',
  '/manifest.webmanifest',
  '/assets/characters/finmate-main.png',
  '/assets/characters/finmate-coach.png',
  '/assets/characters/finmate-birthday.png',
  '/assets/characters/finmate-growth.png',
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS)).then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)
  if (url.pathname.startsWith('/api/')) {
    return
  }
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request).then((cached) => cached || caches.match('/login'))),
  )
})
