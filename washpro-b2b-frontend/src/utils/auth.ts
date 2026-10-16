import { ROLE_LABELS, type Role, type Session } from '../types'

const KEY = 'washpro_session'

export function getSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    // Mã vai trò không còn tồn tại (phiên lưu từ phiên bản cũ, VD RESTAURANT_*): bắt đăng nhập lại
    if (!session.token || !(session.role in ROLE_LABELS) || Date.now() >= session.expiresAt) {
      localStorage.removeItem(KEY)
      return null
    }
    return session
  } catch {
    localStorage.removeItem(KEY)
    return null
  }
}

export function saveSession(session: Session) {
  localStorage.setItem(KEY, JSON.stringify(session))
}

export function clearSession() {
  localStorage.removeItem(KEY)
}

export const isCustomerRole = (role: Role) =>
  role === 'CUSTOMER_STAFF' || role === 'CUSTOMER_MANAGER'

/** Các vai trò được xem công nợ / báo cáo tài chính. */
export const canViewFinance = (role: Role) =>
  role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT' || role === 'CUSTOMER_MANAGER'

export const canManagePrices = (role: Role) =>
  role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT'
