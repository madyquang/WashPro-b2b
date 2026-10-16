import { getSession, isCustomerRole } from '../utils/auth'
import AccountantHome from './home/AccountantHome'
import AdminHome from './home/AdminHome'
import CustomerHome from './home/CustomerHome'
import DriverHome from './home/DriverHome'

/**
 * Trang chủ theo vai trò: mỗi vai trò thấy đúng việc của mình (DESIGN_PLAN.md mục "Trải nghiệm theo vai trò").
 * - Tài xế: việc lấy đồ hôm nay (điện thoại).
 * - Nhân viên / quản lý phía khách: gửi yêu cầu lấy đồ, đơn cần xác nhận, đồ ở xưởng, công nợ (quản lý).
 * - Quản lý xưởng: bảng điều phối.
 * - Kế toán: đơn cần nhập giá, tài chính.
 */
export default function Dashboard() {
  const role = getSession()?.role
  if (!role) return null
  if (role === 'DRIVER') return <DriverHome />
  if (isCustomerRole(role)) return <CustomerHome />
  if (role === 'WORKSHOP_ACCOUNTANT') return <AccountantHome />
  return <AdminHome />
}
