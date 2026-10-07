export type Role =
  | 'DRIVER'
  | 'WORKSHOP_ADMIN'
  | 'WORKSHOP_ACCOUNTANT'
  | 'RESTAURANT_STAFF'
  | 'RESTAURANT_MANAGER'

export type OrderStatus = 'PENDING_CONFIRM' | 'DISCREPANCY' | 'WASHING' | 'DELIVERING' | 'DELIVERY_ISSUE' | 'COMPLETED'
export type PickupStatus = 'OPEN' | 'ACCEPTED' | 'DONE' | 'CANCELLED'
export type TimeSlot = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'ANYTIME'

export type CustomerType = 'RESTAURANT' | 'HOTEL' | 'SPA' | 'COMPANY' | 'HOSPITAL' | 'OTHER'
export type ItemCategory = 'TABLE_LINEN' | 'BEDDING' | 'TOWEL' | 'UNIFORM' | 'OTHER'
export type ServiceType = 'WASH' | 'WASH_IRON' | 'DRY_CLEAN' | 'STAIN_REMOVAL'
export type PriceUnit = 'PIECE' | 'SET' | 'PAIR' | 'KG'

export const ROLE_LABELS: Record<Role, string> = {
  DRIVER: 'Tài xế',
  WORKSHOP_ADMIN: 'Quản lý xưởng',
  WORKSHOP_ACCOUNTANT: 'Kế toán xưởng',
  RESTAURANT_STAFF: 'Nhân viên khách hàng',
  RESTAURANT_MANAGER: 'Quản lý khách hàng',
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

export const STATUS_META: Record<OrderStatus, { label: string; color: string }> = {
  PENDING_CONFIRM: { label: 'Chờ xác nhận', color: 'gold' },
  DISCREPANCY: { label: 'Có sai lệch', color: 'red' },
  WASHING: { label: 'Đang giặt', color: 'blue' },
  DELIVERING: { label: 'Đang giao', color: 'cyan' },
  DELIVERY_ISSUE: { label: 'Sự cố giao trả', color: 'volcano' },
  COMPLETED: { label: 'Hoàn thành', color: 'green' },
}

export const STATUS_ORDER: OrderStatus[] = [
  'PENDING_CONFIRM',
  'DISCREPANCY',
  'WASHING',
  'DELIVERING',
  'DELIVERY_ISSUE',
  'COMPLETED',
]

export const PICKUP_STATUS_META: Record<PickupStatus, { label: string; color: string }> = {
  OPEN: { label: 'Chờ tài xế nhận', color: 'gold' },
  ACCEPTED: { label: 'Tài xế đã nhận', color: 'blue' },
  DONE: { label: 'Đã lấy đồ', color: 'green' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
}

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

export interface Session {
  token: string
  username: string
  fullName: string
  role: Role
  restaurantName: string | null
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

export interface Order {
  id: number
  restaurantId: number
  restaurantName: string
  customerType: CustomerType
  restaurantAddress: string | null
  restaurantPhone: string | null
  contactName: string | null
  driverId: number
  driverName: string
  status: OrderStatus
  totalBags: number
  description: string | null
  items: OrderItem[]
  totalAmount: number
  billable: boolean
  /** Hạn trả đồ sạch. */
  dueAt: string | null
  /** Chưa hoàn thành mà đã quá hạn trả. */
  overdue: boolean
  completedAt: string | null
  createdAt: string
  updatedAt: string
  history: OrderHistory[] | null
}

/** Khách hàng của xưởng (nhà hàng, khách sạn, spa...); tên "Restaurant" giữ theo API. */
export interface Restaurant {
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

export interface PickupRequest {
  id: number
  restaurantId: number
  restaurantName: string
  customerType: CustomerType
  address: string | null
  phone: string | null
  contactName: string | null
  requestedBy: string
  preferredDate: string
  timeSlot: TimeSlot
  estimatedBags: number | null
  note: string | null
  status: PickupStatus
  driverId: number | null
  driverName: string | null
  orderId: number | null
  cancelReason: string | null
  cancelledBy: string | null
  createdAt: string
}

export interface UserAccount {
  id: number
  username: string
  fullName: string | null
  phone: string | null
  role: Role
  restaurantId: number | null
  restaurantName: string | null
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
  restaurantId: number
  restaurantName: string
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
  restaurantId: number
  restaurantName: string
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
  restaurantId: number
  restaurantName: string
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
