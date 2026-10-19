export type Role =
  | 'DRIVER'
  | 'WORKSHOP_ADMIN'
  | 'WORKSHOP_ACCOUNTANT'
  | 'CUSTOMER_STAFF'
  | 'CUSTOMER_MANAGER'

/** Quy trình: chờ lấy → tài xế đang đến → đã tiếp nhận → đang giặt → đã giặt xong → đang giao → hoàn thành. */
export type OrderStatus =
  | 'PICKUP_PENDING'
  | 'PICKUP_ASSIGNED'
  | 'RECEIVED'
  | 'WASHING'
  | 'WASHED'
  | 'DELIVERING'
  | 'DELIVERY_ISSUE'
  | 'COMPLETED'
  /** Quy trình cũ: chỉ còn trong lịch sử của đơn cũ. */
  | 'PENDING_CONFIRM'
  | 'DISCREPANCY'
export type TimeSlot = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'ANYTIME'

export type CustomerType = 'RESTAURANT' | 'HOTEL' | 'SPA' | 'COMPANY' | 'HOSPITAL' | 'OTHER'
export type ItemCategory = 'TABLE_LINEN' | 'BEDDING' | 'TOWEL' | 'UNIFORM' | 'OTHER'
export type ServiceType = 'WASH' | 'WASH_IRON' | 'DRY_CLEAN' | 'STAIN_REMOVAL'
export type PriceUnit = 'PIECE' | 'SET' | 'PAIR' | 'KG'

export const ROLE_LABELS: Record<Role, string> = {
  DRIVER: 'Tài xế',
  WORKSHOP_ADMIN: 'Quản lý xưởng',
  WORKSHOP_ACCOUNTANT: 'Kế toán xưởng',
  CUSTOMER_STAFF: 'Nhân viên khách hàng',
  CUSTOMER_MANAGER: 'Quản lý khách hàng',
}

export const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = {
  RESTAURANT: 'Nhà hàng',
  HOTEL: 'Khách sạn / Homestay',
  SPA: 'Spa / Thẩm mỹ viện',
  COMPANY: 'Công ty / Nhà máy',
  HOSPITAL: 'Bệnh viện / Phòng khám',
  OTHER: 'Khác',
}

/** Thứ tự khai báo cũng là thứ tự hiển thị nhóm đồ. */
export const CATEGORY_META: Record<ItemCategory, { label: string; color: string }> = {
  TABLE_LINEN: { label: 'Đồ bàn ăn & bếp', color: 'orange' },
  BEDDING: { label: 'Chăn ga gối', color: 'geekblue' },
  TOWEL: { label: 'Khăn tắm & khăn mặt', color: 'cyan' },
  UNIFORM: { label: 'Đồng phục', color: 'purple' },
  OTHER: { label: 'Khác', color: 'default' },
}

export const CATEGORY_ORDER = Object.keys(CATEGORY_META) as ItemCategory[]

export const SERVICE_TYPE_LABELS: Record<ServiceType, string> = {
  WASH: 'Giặt sấy',
  WASH_IRON: 'Giặt + là ủi',
  DRY_CLEAN: 'Giặt hấp',
  STAIN_REMOVAL: 'Giặt + tẩy vết bẩn',
}

/** decimal = được nhập số lẻ (chỉ tính theo kg). */
export const UNIT_META: Record<PriceUnit, { label: string; decimal: boolean }> = {
  PIECE: { label: 'cái', decimal: false },
  SET: { label: 'bộ', decimal: false },
  PAIR: { label: 'đôi', decimal: false },
  KG: { label: 'kg', decimal: true },
}

export const STATUS_META: Record<OrderStatus, { label: string }> = {
  PICKUP_PENDING: { label: 'Chờ lấy đồ' },
  PICKUP_ASSIGNED: { label: 'Tài xế đang đến lấy' },
  RECEIVED: { label: 'Đã tiếp nhận' },
  WASHING: { label: 'Đang giặt' },
  WASHED: { label: 'Đã giặt xong' },
  DELIVERING: { label: 'Đang giao trả' },
  DELIVERY_ISSUE: { label: 'Sự cố giao trả' },
  COMPLETED: { label: 'Hoàn thành' },
  PENDING_CONFIRM: { label: 'Chờ khách xác nhận' },
  DISCREPANCY: { label: 'Có sai lệch' },
}

/** Các trạng thái của quy trình hiện tại, theo thứ tự. */
export const STATUS_ORDER: OrderStatus[] = ['PICKUP_PENDING', 'PICKUP_ASSIGNED', 'RECEIVED', 'WASHING', 'WASHED', 'DELIVERING', 'DELIVERY_ISSUE', 'COMPLETED']

/** Đơn còn là yêu cầu lấy đồ (tài xế chưa nhận đồ). */
export const PICKUP_STAGE: OrderStatus[] = ['PICKUP_PENDING', 'PICKUP_ASSIGNED']
export const isPickupStage = (o: Pick<Order, 'status'>) => PICKUP_STAGE.includes(o.status)

export const TIME_SLOT_LABELS: Record<TimeSlot, string> = {
  MORNING: 'Sáng (7h–11h)',
  AFTERNOON: 'Chiều (13h–17h)',
  EVENING: 'Tối (17h–21h)',
  ANYTIME: 'Giờ nào cũng được',
}

/** Lựa chọn nhanh cho thiết lập khách hàng. */
export const TURNAROUND_OPTIONS = [12, 24, 48, 72, 96]
export const PAYMENT_TERM_OPTIONS = [0, 7, 15, 30, 45, 60]
export const paymentTermLabel = (days: number) => (days === 0 ? 'Thanh toán ngay' : `Công nợ ${days} ngày`)

/** Báo cáo hiệu suất. Tỉ lệ 0..1, null khi chưa đủ dữ liệu. */
export interface PerformanceSummary {
  orders: number
  bags: number
  orderValue: number
  delivered: number
  onTime: number
  onTimeRate: number | null
  avgTurnaroundHours: number | null
  discrepancyOrders: number
  discrepancyRate: number | null
  issueOrders: number
  issueRate: number | null
  pickupsRequested: number
  pickupsDone: number
  pickupsCancelled: number
  avgPickupHours: number | null
}

export interface PerformanceCustomerRow {
  customerId: number
  customerName: string
  orders: number
  bags: number
  orderValue: number
  delivered: number
  onTimeRate: number | null
  avgTurnaroundHours: number | null
  discrepancyRate: number | null
  issueRate: number | null
}

export interface PerformanceDriverRow {
  driverId: number
  driverName: string
  orders: number
  bags: number
  pickupsDone: number
  avgPickupHours: number | null
  discrepancyRate: number | null
}

export interface PerformanceReport {
  from: string
  to: string
  summary: PerformanceSummary
  customers: PerformanceCustomerRow[]
  /** Chỉ có với tài khoản phía xưởng. */
  drivers: PerformanceDriverRow[] | null
}

/** Một điểm dừng trên lộ trình tài xế: lấy đồ (đơn đã nhận việc) hoặc giao trả (đơn đang giao). */
export interface RouteStop {
  type: 'PICKUP' | 'DELIVERY'
  /** Mã đơn. */
  id: number
  customerId: number
  customerName: string
  address: string | null
  /** Quận / huyện đoán từ địa chỉ. */
  area: string | null
  phone: string | null
  contactName: string | null
  date: string | null
  timeSlot: TimeSlot | null
  bags: number | null
  note: string | null
  late: boolean
}

export interface RoutePlan {
  date: string
  driverId: number
  driverName: string
  workshopAddress: string | null
  stops: RouteStop[]
}

/** Mã QR VietQR chuyển khoản số còn phải thanh toán (ảnh dạng data URL). */
export interface PaymentQr {
  amount: number
  content: string
  bankName: string | null
  bankAccount: string
  accountHolder: string | null
  image: string
}

export type AuditCategory = 'PRICE' | 'PAYMENT' | 'ORDER' | 'CUSTOMER' | 'ACCOUNT'

/** Một dòng nhật ký thay đổi. */
export interface AuditEntry {
  id: number
  createdAt: string
  actor: string
  actorRole: string
  category: AuditCategory
  action: string
  actionLabel: string
  entityId: number | null
  customerId: number | null
  summary: string
  /** Giá trị cũ → mới, mỗi thay đổi một dòng. */
  details: string | null
}

export type AttachmentKind = 'PICKUP_PHOTO' | 'PICKUP_SIGNATURE' | 'DELIVERY_PHOTO' | 'DELIVERY_SIGNATURE' | 'ISSUE_PHOTO'

export const ATTACHMENT_LABELS: Record<AttachmentKind, string> = {
  PICKUP_PHOTO: 'Ảnh lấy đồ',
  PICKUP_SIGNATURE: 'Chữ ký lấy đồ',
  DELIVERY_PHOTO: 'Ảnh giao trả',
  DELIVERY_SIGNATURE: 'Chữ ký nhận đồ',
  ISSUE_PHOTO: 'Ảnh thiếu / hỏng',
}

export interface OrderAttachment {
  id: number
  kind: AttachmentKind
  kindLabel: string
  contentType: string
  sizeBytes: number
  uploadedBy: string
  createdAt: string
}

export type NotificationType =
  | 'PICKUP_NEW'
  | 'PICKUP_ACCEPTED'
  | 'PICKUP_ASSIGNED'
  | 'PICKUP_CANCELLED'
  | 'PICKUP_RESCHEDULED'
  | 'ORDER_RECEIVED'
  | 'ORDER_WASHED'
  | 'DELIVERY_ASSIGNED'
  | 'ORDER_DELIVERING'
  | 'ORDER_COMPLETED'
  | 'ORDER_RECEIPT_ISSUE'
  | 'ORDER_CANCELLED'
  | 'PAYMENT_RECORDED'
  | 'OVERDUE_ORDERS'
  | 'OVERDUE_DEBT'
  | 'ORDER_TO_CONFIRM'
  | 'ORDER_DISCREPANCY'

/** Thông báo trong ứng dụng (đặt tên khác Notification của trình duyệt). */
export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  body: string | null
  /** Đường dẫn mở khi bấm, VD /orders?open=12 */
  link: string | null
  createdAt: string
  read: boolean
}

export interface Session {
  token: string
  username: string
  fullName: string
  role: Role
  customerName: string | null
  expiresAt: number
}

export interface OrderItem {
  id: number
  /** null = đồ ngoài bảng giá (nhập tay). */
  servicePriceId: number | null
  itemName: string
  category: ItemCategory
  serviceType: ServiceType
  unit: PriceUnit
  quantity: number
  unitPrice: number | null
  lineTotal: number | null
}

export interface OrderHistory {
  fromStatus: OrderStatus | null
  toStatus: OrderStatus
  actor: string
  note: string | null
  createdAt: string
}

/** Một dòng khách khai khi gửi yêu cầu lấy đồ (không có giá). */
export interface DeclaredItem {
  id: number
  servicePriceId: number | null
  itemName: string
  serviceType: ServiceType
  unit: PriceUnit
  quantity: number
}

/** Ghi chú nội bộ của xưởng / tài xế trên đơn (khách không thấy). */
export interface OrderNote {
  id: number
  author: string
  authorRole: Role
  body: string
  createdAt: string
}

export interface Order {
  id: number
  customerId: number
  customerName: string
  customerType: CustomerType
  customerAddress: string | null
  customerPhone: string | null
  contactName: string | null
  /** Tài xế lấy đồ; null khi chưa có ai nhận việc. */
  driverId: number | null
  driverName: string | null
  driverUsername: string | null
  /** Tài xế giao trả; null khi chưa giao. */
  deliveryDriverId: number | null
  deliveryDriverName: string | null
  deliveryDriverUsername: string | null
  status: OrderStatus
  /** Số túi thực tế khi nhận đồ; null khi chưa lấy đồ. */
  totalBags: number | null
  description: string | null
  /** Yêu cầu lấy đồ: người gửi, ngày / khung giờ hẹn, số túi ước tính, lời dặn tài xế. */
  requestedBy: string | null
  preferredDate: string | null
  timeSlot: TimeSlot | null
  estimatedBags: number | null
  pickupNote: string | null
  /** Đã qua ngày hẹn lấy mà chưa lấy. */
  pickupLate: boolean
  /** Số khách khai khi gửi yêu cầu (đối chiếu với items là số thực nhận). */
  declaredItems: DeclaredItem[]
  items: OrderItem[]
  totalAmount: number
  billable: boolean
  /** Lúc tài xế nhận đồ. */
  receivedAt: string | null
  /** Hạn trả đồ sạch. */
  dueAt: string | null
  /** Chưa hoàn thành mà đã quá hạn trả. */
  overdue: boolean
  completedAt: string | null
  /** Chỉ có với đơn / yêu cầu đã hủy. */
  cancelReason: string | null
  cancelledBy: string | null
  /** Lúc khách gửi yêu cầu lấy đồ (tạo đơn). */
  createdAt: string
  updatedAt: string
  history: OrderHistory[] | null
  /** Chỉ có khi phía xưởng / tài xế xem chi tiết một đơn. */
  notes: OrderNote[] | null
}

/** Một trang đơn hàng từ GET /orders (lọc, phân trang phía máy chủ). */
export interface OrderPage {
  content: Order[]
  total: number
  /** Bắt đầu từ 0. */
  page: number
  size: number
  /** Số đơn theo trạng thái, đã áp các bộ lọc khác trừ trạng thái. */
  counts: Record<OrderStatus, number>
  /** Số đơn trễ hạn, đã áp các bộ lọc khác trừ trạng thái và "chỉ trễ hạn". */
  overdueCount: number
}

/** Khách hàng của xưởng (nhà hàng, khách sạn, spa...); tên "Customer" giữ theo API. */
export interface Customer {
  id: number
  name: string
  customerType: CustomerType
  address: string
  phone: string | null
  contactName: string | null
  /** Cam kết trả đồ (giờ kể từ lúc nhận). */
  turnaroundHours: number
  /** Số ngày được nợ sau khi đơn hoàn thành (0 = trả ngay). */
  paymentTermDays: number
  active: boolean
  createdAt: string
}

export interface UserAccount {
  id: number
  username: string
  fullName: string | null
  phone: string | null
  role: Role
  customerId: number | null
  customerName: string | null
  enabled: boolean
  createdAt: string
}

export interface CatalogItem {
  id: number
  name: string
  category: ItemCategory
  serviceType: ServiceType
  unit: PriceUnit
}

export interface ServicePrice extends CatalogItem {
  unitPrice: number
  active: boolean
}

export interface CustomerPriceRow {
  servicePriceId: number
  name: string
  category: ItemCategory
  serviceType: ServiceType
  unit: PriceUnit
  active: boolean
  basePrice: number
  customPrice: number | null
  effectivePrice: number
}

export interface Payment {
  id: number
  customerId: number
  customerName: string
  amount: number
  paidDate: string
  note: string | null
  createdBy: string
  createdAt: string
  voided: boolean
  voidedBy: string | null
  voidReason: string | null
}

export interface MonthRevenue {
  month: string
  orders: number
  revenue: number
}

export interface DebtRow {
  customerId: number
  customerName: string
  customerType: CustomerType
  active: boolean
  paymentTermDays: number
  completedOrders: number
  billed: number
  paid: number
  balance: number
  notDue: number
  overdue1to30: number
  overdue31to60: number
  overdueOver60: number
  overdueTotal: number
  /** Hạn thanh toán sớm nhất còn nợ. */
  oldestUnpaidDue: string | null
}

export interface Dashboard {
  countsByStatus: Record<OrderStatus, number>
  totalOrders: number
  ordersToday: number
  overdueOrders: number
  dueToday: number
  openPickups: number
  billed: number | null
  paid: number | null
  balance: number | null
  overdueDebt: number | null
}

export interface Statement {
  customerId: number
  customerName: string
  customerType: CustomerType
  address: string | null
  paymentTermDays: number
  month: string
  openingBalance: number
  orders: { id: number; completedAt: string; paymentDue: string; itemLines: number; amount: number }[]
  payments: { id: number; paidDate: string; amount: number; note: string | null }[]
  totalBilled: number
  totalPaid: number
  closingBalance: number
}
