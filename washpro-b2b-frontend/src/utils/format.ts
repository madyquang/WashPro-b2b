import axios from 'axios'

const currency = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

export const formatCurrency = (value: number | null | undefined) =>
  value === null || value === undefined ? '—' : currency.format(value)

export const formatDateTime = (iso: string | null | undefined) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleString('vi-VN', { hour12: false, dateStyle: 'short', timeStyle: 'short' })
}

export const formatDate = (iso: string | null | undefined) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('vi-VN')
}

/** Ngày hôm nay dạng yyyy-MM-dd theo giờ địa phương (dùng cho input type="date"). */
export const todayIso = () => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Chuyển "2026-10-06T14:30:00" thành giá trị cho input datetime-local ("2026-10-06T14:30"). */
export const toLocalInput = (iso: string | null | undefined) => (iso ? iso.slice(0, 16) : '')

/** Tháng hiện tại dạng yyyy-MM. */
export const currentMonth = () => todayIso().slice(0, 7)

/** Định dạng ô nhập tiền: 1.500.000 */
export const moneyFormatter = (value: string | number | undefined) =>
  `${value ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')

export const moneyParser = (value: string | undefined) => Number((value ?? '').replace(/\D/g, ''))

/** Lấy thông báo lỗi dễ hiểu từ mọi loại lỗi (kể cả lỗi mạng). */
export function getErrorMessage(error: unknown, fallback = 'Đã có lỗi xảy ra, vui lòng thử lại'): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng hoặc thử lại sau'
    const data = error.response.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message) return data.message
    if (error.response.status === 403) return 'Bạn không có quyền thực hiện thao tác này'
    if (error.response.status >= 500) return 'Hệ thống gặp sự cố, vui lòng thử lại sau'
  }
  return fallback
}
