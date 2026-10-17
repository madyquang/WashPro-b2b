import axiosClient from '../api/axiosClient'

/** Trình duyệt hỗ trợ thông báo đẩy và đang chạy bản build (có service worker). */
export const pushSupported = () =>
  import.meta.env.PROD && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

/** 'on' đã bật trên thiết bị này; 'off' chưa bật; 'blocked' người dùng đã chặn trong cài đặt trình duyệt. */
export type PushState = 'on' | 'off' | 'blocked' | 'unsupported'

/** Service worker đang chạy; null nếu không đăng ký được (chế độ riêng tư...) sau 3 giây. */
function registration(): Promise<ServiceWorkerRegistration | null> {
  return Promise.race([navigator.serviceWorker.ready, new Promise<null>((r) => setTimeout(() => r(null), 3000))])
}

async function currentSubscription() {
  const reg = await registration()
  return reg ? reg.pushManager.getSubscription() : null
}

export async function pushState(): Promise<PushState> {
  if (!pushSupported() || !(await registration())) return 'unsupported'
  if (Notification.permission === 'denied') return 'blocked'
  return (await currentSubscription()) && Notification.permission === 'granted' ? 'on' : 'off'
}

function base64UrlToBytes(s: string) {
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (s.length % 4)) % 4)
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
}

/** Xin quyền, đăng ký với máy chủ push của trình duyệt và gửi đăng ký lên xưởng. */
export async function enablePush(): Promise<PushState> {
  if (!pushSupported()) return 'unsupported'
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return permission === 'denied' ? 'blocked' : 'off'
  const reg = await registration()
  if (!reg) return 'unsupported'
  const { data } = await axiosClient.get<{ publicKey: string }>('/push/public-key')
  let sub = await reg.pushManager.getSubscription()
  if (!sub) {
    sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(data.publicKey) })
  }
  await axiosClient.post('/push/subscriptions', sub.toJSON())
  return 'on'
}

/** Tắt trên thiết bị này. */
export async function disablePush(): Promise<PushState> {
  const sub = pushSupported() ? await currentSubscription() : null
  if (sub) {
    await axiosClient.post('/push/subscriptions/remove', { endpoint: sub.endpoint }).catch(() => {})
    await sub.unsubscribe().catch(() => {})
  }
  return 'off'
}

/**
 * Sau khi đăng nhập: thiết bị đã bật thông báo thì gửi lại đăng ký lên xưởng (gắn với tài khoản đang đăng nhập;
 * khôi phục nếu xưởng đã xóa, VD sau "đăng xuất mọi thiết bị").
 */
export async function syncPush() {
  if (!pushSupported() || Notification.permission !== 'granted') return
  const sub = await currentSubscription()
  if (sub) await axiosClient.post('/push/subscriptions', sub.toJSON()).catch(() => {})
}

/**
 * Gọi trước khi đăng xuất: thiết bị này không nhận thông báo của tài khoản vừa đăng xuất nữa
 * (người khác đăng nhập trên cùng máy không thấy). Không chờ quá 1,5 giây.
 */
export async function forgetThisDevice() {
  if (!pushSupported()) return
  await Promise.race([disablePush(), new Promise((r) => setTimeout(r, 1500))]).catch(() => {})
}
