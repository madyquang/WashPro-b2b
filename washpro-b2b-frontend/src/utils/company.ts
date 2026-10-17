import { useEffect, useState } from 'react'
import axiosClient from '../api/axiosClient'

export interface CompanyInfo {
  name: string
  /** null = xưởng chưa cấu hình số hotline. */
  hotline: string | null
}

let cached: Promise<CompanyInfo | null> | null = null

/** Thông tin xưởng, tải một lần cho cả phiên (lỗi mạng thì lần sau thử lại). */
function loadCompany() {
  if (!cached) {
    cached = axiosClient
      .get<CompanyInfo>('/company')
      .then((r) => r.data)
      .catch(() => {
        cached = null
        return null
      })
  }
  return cached
}

export function useCompany() {
  const [info, setInfo] = useState<CompanyInfo | null>(null)
  useEffect(() => {
    let alive = true
    void loadCompany().then((c) => alive && setInfo(c))
    return () => {
      alive = false
    }
  }, [])
  return info
}

/** Liên kết gọi điện: chỉ giữ chữ số và dấu + (số nhập kiểu "0909 123 456" vẫn gọi được). */
export const telHref = (phone: string) => `tel:${phone.replace(/[^\d+]/g, '')}`
