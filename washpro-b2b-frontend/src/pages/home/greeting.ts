import { ROLE_LABELS } from '../../types'

export const greeting = () => {
  const h = new Date().getHours()
  return h < 11 ? 'Chào buổi sáng' : h < 14 ? 'Chào buổi trưa' : h < 18 ? 'Chào buổi chiều' : 'Chào buổi tối'
}

/** Họ tên là chức danh chứ không phải tên người (VD "Quản lý xưởng", "Nhân viên Khách sạn Hoa Sen"). */
const TITLE_PREFIXES = ['nhân viên', 'quản lý', 'tài xế', 'kế toán', 'admin']
const isTitle = (name: string) =>
  Object.values(ROLE_LABELS).includes(name) || TITLE_PREFIXES.some((p) => name.toLowerCase().startsWith(p))

/** Người Việt gọi theo tên (từ cuối của họ tên). Họ tên là chức danh thì không gọi tên (tránh "Chào buổi sáng, Sen"). */
export const callName = (fullName: string): string | null => {
  const name = fullName.trim()
  if (!name || isTitle(name)) return null
  return name.split(/\s+/).pop() ?? name
}

/** "Chào buổi sáng, Mai" hoặc "Chào buổi sáng" khi không có tên người. */
export const greetingFor = (fullName: string) => {
  const name = callName(fullName)
  return name ? `${greeting()}, ${name}` : greeting()
}

/** "Thứ Bảy, 10/10/2026" */
export const todayLabel = () =>
  new Date().toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/^./, (c) => c.toUpperCase())
