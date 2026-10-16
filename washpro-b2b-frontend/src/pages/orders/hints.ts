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
  { key: 'todo', label: 'Cần bạn xác nhận', statuses: ['PENDING_CONFIRM', 'DISCREPANCY', 'DELIVERING'], empty: 'Không có đơn nào cần bạn xác nhận.' },
  { key: 'washing', label: 'Đang ở xưởng', statuses: ['WASHING', 'DELIVERY_ISSUE'], empty: 'Không có đồ nào đang ở xưởng.' },
  { key: 'done', label: 'Hoàn thành', statuses: ['COMPLETED'], empty: 'Chưa có đơn hoàn thành.' },
  { key: 'all', label: 'Tất cả', statuses: [], empty: 'Chưa có đơn giặt nào.' },
]

const DRIVER_VIEWS: OrderView[] = [
  { key: 'all', label: 'Tất cả', statuses: [], empty: 'Bạn chưa tạo đơn nào.' },
  { key: 'waiting', label: 'Chờ khách xác nhận', statuses: ['PENDING_CONFIRM', 'DISCREPANCY'], empty: 'Không có đơn nào đang chờ khách xác nhận.' },
  { key: 'progress', label: 'Đang giặt / giao', statuses: ['WASHING', 'DELIVERING', 'DELIVERY_ISSUE'], empty: 'Không có đơn nào đang giặt hoặc giao.' },
  { key: 'done', label: 'Hoàn thành', statuses: ['COMPLETED'], empty: 'Chưa có đơn hoàn thành.' },
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
    if (o.status === 'PENDING_CONFIRM') return 'Tài xế đã đếm đồ. Kiểm lại số lượng rồi xác nhận để xưởng bắt đầu giặt.'
    if (o.status === 'DISCREPANCY') return 'Số lượng đang có sai lệch. Kiểm lại rồi xác nhận, hoặc báo lại nếu vẫn chưa đúng.'
    if (o.status === 'DELIVERING') return 'Đồ sạch đang được giao. Kiểm đủ rồi bấm "Đã nhận đủ đồ".'
    if (o.status === 'DELIVERY_ISSUE') return 'Xưởng đang xử lý phần thiếu / hỏng bạn đã báo.'
    return undefined
  }
  if (role === 'DRIVER') {
    if (o.status === 'PENDING_CONFIRM') return 'Đang chờ khách xác nhận số lượng.'
    if (o.status === 'DISCREPANCY') return 'Có sai lệch số lượng, chờ khách xác nhận lại hoặc xưởng mở lại đơn.'
    if (o.status === 'DELIVERING') return 'Đang giao trả, chờ khách xác nhận đã nhận đủ.'
    return undefined
  }
  if (role === 'WORKSHOP_ACCOUNTANT' && !o.billable && o.status !== 'COMPLETED') return 'Còn dòng chưa có đơn giá.'
  return undefined
}
