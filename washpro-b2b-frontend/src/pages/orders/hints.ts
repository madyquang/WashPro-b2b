import { isCustomerRole } from '../../utils/auth'
import type { Order, OrderStatus, Role } from '../../types'

/** Một nhóm việc trên trang Đơn hàng: gom nhiều trạng thái theo cách người dùng nghĩ về công việc. */
export interface OrderView {
  key: string
  label: string
  /** Rỗng = mọi trạng thái. */
  statuses: OrderStatus[]
  empty: string
}

const CUSTOMER_VIEWS: OrderView[] = [
  { key: 'todo', label: 'Đang giao tới', statuses: ['DELIVERING'], empty: 'Không có đơn nào đang giao tới.' },
  { key: 'pickup', label: 'Chờ lấy đồ', statuses: ['PICKUP_PENDING', 'PICKUP_ASSIGNED'], empty: 'Không có yêu cầu lấy đồ nào đang chờ.' },
  { key: 'washing', label: 'Đang ở xưởng', statuses: ['RECEIVED', 'WASHING', 'WASHED', 'DELIVERY_ISSUE'], empty: 'Không có đồ nào đang ở xưởng.' },
  { key: 'done', label: 'Hoàn thành', statuses: ['COMPLETED'], empty: 'Chưa có đơn hoàn thành.' },
  { key: 'all', label: 'Tất cả', statuses: [], empty: 'Chưa có đơn giặt nào.' },
]

const DRIVER_VIEWS: OrderView[] = [
  { key: 'progress', label: 'Đang xử lý', statuses: ['RECEIVED', 'WASHING', 'WASHED', 'DELIVERING', 'DELIVERY_ISSUE'], empty: 'Không có đơn nào đang xử lý.' },
  { key: 'done', label: 'Hoàn thành', statuses: ['COMPLETED'], empty: 'Chưa có đơn hoàn thành.' },
  { key: 'all', label: 'Tất cả', statuses: [], empty: 'Chưa có đơn nào.' },
]

/** Nhóm việc cho tài khoản phía khách và tài xế; xưởng dùng tab theo từng trạng thái (null). */
export function fieldViews(role: Role): OrderView[] | null {
  if (isCustomerRole(role)) return CUSTOMER_VIEWS
  if (role === 'DRIVER') return DRIVER_VIEWS
  return null
}

/** Lời nhắc ngắn trên thẻ đơn: người dùng cần làm gì tiếp theo (hoặc đang chờ ai). */
export function orderHint(o: Order, role: Role): string | undefined {
  if (isCustomerRole(role)) {
    if (o.status === 'PICKUP_PENDING') return 'Xưởng đã nhận yêu cầu, đang sắp xếp tài xế.'
    if (o.status === 'PICKUP_ASSIGNED') return `Tài xế ${o.driverName ?? ''} sẽ đến lấy đồ.`
    if (o.status === 'RECEIVED') return 'Tài xế đã nhận đồ, đang mang về xưởng.'
    if (o.status === 'WASHED') return 'Đồ đã giặt xong, xưởng sắp giao trả.'
    if (o.status === 'DELIVERING') return 'Đồ sạch đang được giao. Kiểm đủ rồi bấm "Đã nhận đủ đồ", thiếu / hỏng thì báo ngay.'
    if (o.status === 'DELIVERY_ISSUE') return 'Xưởng đang xử lý phần thiếu / hỏng bạn đã báo.'
    return undefined
  }
  if (role === 'DRIVER') {
    if (o.status === 'PICKUP_ASSIGNED') return 'Kiểm số lượng cùng khách, khách ký rồi bấm "Đã nhận đồ".'
    if (o.status === 'WASHED') return 'Đồ đã giặt xong, chờ tài xế nhận giao.'
    if (o.status === 'DELIVERING') return 'Giao xong thì cho khách ký nhận rồi bấm "Giao xong".'
    return undefined
  }
  if (role === 'WORKSHOP_ADMIN') {
    if (o.status === 'RECEIVED') return 'Đồ đang về xưởng: bấm "Bắt đầu giặt" khi nhận đủ.'
    if (o.status === 'WASHED') return 'Giặt xong, chờ tài xế giao.'
    return undefined
  }
  if (role === 'WORKSHOP_ACCOUNTANT' && !o.billable && o.status !== 'COMPLETED') return 'Còn dòng chưa có đơn giá.'
  return undefined
}
