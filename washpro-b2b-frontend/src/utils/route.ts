import type { RouteStop } from '../types'

/** Google Maps nhận tối đa 9 điểm dừng giữa đường + 1 điểm đến trong một liên kết. */
const MAX_PER_LINK = 10

export const stopKey = (s: RouteStop) => `${s.type}-${s.id}`

const place = (s: RouteStop) => s.address?.trim() || s.customerName

/**
 * Liên kết Google Maps chỉ đường qua nhiều điểm (không cần khóa API). Quá 10 điểm thì chia nhiều đoạn,
 * đoạn sau bắt đầu từ điểm cuối của đoạn trước. Đoạn đầu không ghi điểm xuất phát: Maps dùng vị trí hiện tại.
 */
export function mapsLinks(stops: RouteStop[], finalDestination?: string | null): { label: string; url: string }[] {
  const places = stops.map(place)
  if (finalDestination) places.push(finalDestination)
  const links: { label: string; url: string }[] = []
  let origin: string | null = null
  for (let start = 0; start < places.length; start += MAX_PER_LINK) {
    const chunk = places.slice(start, start + MAX_PER_LINK)
    const params = new URLSearchParams({ api: '1', travelmode: 'driving', destination: chunk[chunk.length - 1] })
    if (origin) params.set('origin', origin)
    if (chunk.length > 1) params.set('waypoints', chunk.slice(0, -1).join('|'))
    const from = start + 1
    const to = start + chunk.length
    links.push({ label: places.length > MAX_PER_LINK ? `Điểm ${from}–${to}` : 'Cả lộ trình', url: `https://www.google.com/maps/dir/?${params}` })
    origin = chunk[chunk.length - 1]
  }
  return links
}

/** Thứ tự tài xế tự sắp, lưu trên máy (theo tài xế và ngày). */
const orderKey = (driverId: number, date: string) => `washpro_route_${driverId}_${date}`

export function loadOrder(driverId: number, date: string): string[] | null {
  try {
    const raw = localStorage.getItem(orderKey(driverId, date))
    return raw ? (JSON.parse(raw) as string[]) : null
  } catch {
    return null
  }
}

export function saveOrder(driverId: number, date: string, keys: string[] | null) {
  try {
    if (keys) localStorage.setItem(orderKey(driverId, date), JSON.stringify(keys))
    else localStorage.removeItem(orderKey(driverId, date))
  } catch {
    // Trình duyệt chặn lưu trữ: thứ tự chỉ giữ tới khi tải lại trang
  }
}

/** Áp thứ tự đã lưu: điểm đã sắp giữ nguyên chỗ, điểm mới (chưa có trong thứ tự) nối sau theo thứ tự gợi ý. */
export function applyOrder(stops: RouteStop[], keys: string[] | null): RouteStop[] {
  if (!keys) return stops
  const byKey = new Map(stops.map((s) => [stopKey(s), s]))
  const ordered = keys.map((k) => byKey.get(k)).filter((s): s is RouteStop => !!s)
  const placed = new Set(ordered.map(stopKey))
  return [...ordered, ...stops.filter((s) => !placed.has(stopKey(s)))]
}
