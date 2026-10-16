import { useSyncExternalStore } from 'react'

/** Đăng ký service worker (public/sw.js). Chỉ ở bản build: khi phát triển (vite dev) không lưu đệm gì. */
export function registerServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Trình duyệt không cho đăng ký (VD chế độ riêng tư): ứng dụng vẫn chạy bình thường, chỉ không mở được khi mất mạng
    })
  })
}

// ===================== Trạng thái mạng =====================

function subscribeOnline(cb: () => void) {
  window.addEventListener('online', cb)
  window.addEventListener('offline', cb)
  return () => {
    window.removeEventListener('online', cb)
    window.removeEventListener('offline', cb)
  }
}

/** true khi trình duyệt báo đang có mạng. */
export const useOnline = () => useSyncExternalStore(subscribeOnline, () => navigator.onLine)

// ===================== Cài ứng dụng lên màn hình chính =====================

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

let deferred: BeforeInstallPromptEvent | null = null
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

// Android / Chrome / Edge: trình duyệt báo "có thể cài", giữ lại để hiện nút Cài ứng dụng của mình
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault()
  deferred = e as BeforeInstallPromptEvent
  notify()
})
window.addEventListener('appinstalled', () => {
  deferred = null
  notify()
})

function subscribeInstall(cb: () => void) {
  listeners.add(cb)
  return () => {
    listeners.delete(cb)
  }
}

/** Đang mở dưới dạng ứng dụng đã cài (không có thanh địa chỉ). */
export const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true

/** iPhone / iPad: Safari không có nút cài tự động, phải hướng dẫn "Chia sẻ → Thêm vào MH chính". */
export const isIos = () => /iphone|ipad|ipod/i.test(navigator.userAgent)

export function useInstallPrompt() {
  const canInstall = useSyncExternalStore(subscribeInstall, () => deferred !== null)
  const install = async () => {
    const e = deferred
    if (!e) return false
    await e.prompt()
    const { outcome } = await e.userChoice
    deferred = null
    notify()
    return outcome === 'accepted'
  }
  return { canInstall, install }
}
