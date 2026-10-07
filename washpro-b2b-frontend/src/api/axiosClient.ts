import axios from 'axios'
import { clearSession, getSession } from '../utils/auth'

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api',
  timeout: 20000,
})

axiosClient.interceptors.request.use((config) => {
  const session = getSession()
  if (session) {
    config.headers.Authorization = `Bearer ${session.token}`
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginRequest = String(error.config?.url ?? '').includes('/auth/login')
    // Token hết hạn hoặc không hợp lệ: xóa phiên và đưa về trang đăng nhập
    if (error.response?.status === 401 && !isLoginRequest) {
      clearSession()
      if (window.location.pathname !== '/login') {
        window.location.assign('/login?expired=1')
      }
    }
    return Promise.reject(error)
  },
)

export default axiosClient
