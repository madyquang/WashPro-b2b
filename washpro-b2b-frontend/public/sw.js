/*
 * Service worker của THANHXUAN (viết tay, không dùng thư viện).
 * - Trang (index.html): lấy mạng trước, mất mạng thì dùng bản đã lưu -> mở được ứng dụng khi sóng yếu.
 * - /assets/* (tên có mã băm, không bao giờ đổi nội dung): lưu lần đầu rồi dùng mãi.
 * - Phông chữ Google: dùng bản đã lưu, cập nhật ngầm.
 * - API (/api/*) và mọi yêu cầu khác: KHÔNG đụng tới (dữ liệu nghiệp vụ luôn lấy mới, có token).
 * - Thông báo đẩy: hiện thông báo của hệ điều hành; bấm vào thì mở / chuyển tới đúng trang.
 * Đổi VERSION khi sửa file này để xóa bộ nhớ đệm cũ.
 */
const VERSION = 'v4'
const PAGES = `thanhxuan-pages-${VERSION}`
const ASSETS = `thanhxuan-assets-${VERSION}`
const FONTS = `thanhxuan-fonts-${VERSION}`

self.addEventListener('install', (event) => {
  event.waitUntil(
    Promise.all([
      caches.open(PAGES).then((cache) => cache.addAll(['/', '/manifest.webmanifest', '/icons/icon-192.png', '/favicon.png'])),
      precacheBuild(),
    ]).then(() => self.skipWaiting()),
  )
})

/**
 * Lưu sẵn mọi gói JS / CSS của bản build (theo dist/.vite/manifest.json do Vite sinh) để mất mạng vẫn mở được cả
 * những trang chưa từng vào. Không có manifest (khi phát triển) thì bỏ qua.
 */
async function precacheBuild() {
  try {
    const res = await fetch('/.vite/manifest.json', { cache: 'no-cache' })
    if (!res.ok) return
    const files = new Set()
    for (const chunk of Object.values(await res.json())) {
      if (chunk.file) files.add('/' + chunk.file)
      for (const css of chunk.css ?? []) files.add('/' + css)
    }
    const cache = await caches.open(ASSETS)
    await cache.addAll([...files])
  } catch {
    // Lưu sẵn không được (mạng chập chờn): các gói vẫn được lưu dần khi người dùng mở trang
  }
}

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => ![PAGES, ASSETS, FONTS].includes(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  if (req.method !== 'GET') return
  const url = new URL(req.url)

  if (url.origin === self.location.origin) {
    if (url.pathname.startsWith('/api/')) return
    if (req.mode === 'navigate') {
      event.respondWith(networkFirstPage(req))
      return
    }
    if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
      event.respondWith(cacheFirst(req, ASSETS))
    }
    return
  }

  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    event.respondWith(staleWhileRevalidate(req, FONTS))
  }
})

/** Ứng dụng một trang: mọi đường dẫn đều là index.html; mất mạng thì trả bản index đã lưu. */
async function networkFirstPage(req) {
  const cache = await caches.open(PAGES)
  try {
    const res = await fetch(req)
    if (res.ok) cache.put('/', res.clone())
    return res
  } catch {
    return (await cache.match('/')) || Response.error()
  }
}

async function cacheFirst(req, name) {
  const cache = await caches.open(name)
  // ignoreVary: gói lưu sẵn (không kèm header Origin) vẫn dùng được cho yêu cầu module script (có Origin)
  const hit = await cache.match(req, { ignoreVary: true })
  if (hit) return hit
  const res = await fetch(req)
  if (res.ok) cache.put(req, res.clone())
  return res
}

async function staleWhileRevalidate(req, name) {
  const cache = await caches.open(name)
  const hit = await cache.match(req)
  const update = fetch(req)
    .then((res) => {
      if (res.ok || res.type === 'opaque') cache.put(req, res.clone())
      return res
    })
    .catch(() => hit)
  return hit || update
}

// ===================== Thông báo đẩy (Web Push) =====================

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { title: event.data ? event.data.text() : '' }
  }
  const options = {
    body: data.body || '',
    icon: '/icons/icon-192.png',
    lang: 'vi',
    data: { link: data.link || '/' },
  }
  // Bản tin hằng ngày: bản mới thay bản cũ cùng loại; các việc khác hiện riêng từng cái
  if (data.tag && data.tag.startsWith('OVERDUE')) {
    options.tag = data.tag
    options.renotify = true
  }
  event.waitUntil(self.registration.showNotification(data.title || 'THANHXUAN', options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const link = (event.notification.data && event.notification.data.link) || '/'
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      for (const w of windows) {
        if (new URL(w.url).origin === self.location.origin && 'focus' in w) {
          await w.focus()
          if ('navigate' in w) await w.navigate(link)
          return
        }
      }
      await self.clients.openWindow(link)
    })(),
  )
})
