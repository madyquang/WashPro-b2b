import type { Role, Session } from '../types'

const KEY = 'washpro_session'

export function getSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (!session.token || !session.role || Date.now() >= session.expiresAt) {
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

export const isRestaurantRole = (role: Role) =>
  role === 'RESTAURANT_STAFF' || role === 'RESTAURANT_MANAGER'

/** Các vai trò được xem công nợ / báo cáo tài chính. */
export const canViewFinance = (role: Role) =>
  role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT' || role === 'RESTAURANT_MANAGER'

export const canManagePrices = (role: Role) =>
  role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT'
