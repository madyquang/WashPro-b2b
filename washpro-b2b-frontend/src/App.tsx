import type { ReactElement } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import MainLayout from './components/MainLayout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Orders from './pages/Orders'
import Pickups from './pages/Pickups'
import Restaurants from './pages/Restaurants'
import Accounts from './pages/Accounts'
import Prices from './pages/Prices'
import Finance from './pages/Finance'
import Profile from './pages/Profile'
import { antdTheme } from './theme'
import { getSession } from './utils/auth'
import type { Role } from './types'

function ProtectedRoute({ children }: { children: ReactElement }) {
  return getSession() ? children : <Navigate to="/login" replace />
}

/** Chặn truy cập trực tiếp bằng URL vào trang không thuộc vai trò (backend vẫn kiểm tra quyền thật). */
function RoleRoute({ roles, children }: { roles: Role[]; children: ReactElement }) {
  const session = getSession()
  return session && roles.includes(session.role) ? children : <Navigate to="/" replace />
}

export default function App() {
  return (
    <ConfigProvider locale={viVN} theme={antdTheme}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            element={
              <ProtectedRoute>
                <MainLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Dashboard />} />
            <Route path="orders" element={<Orders />} />
            <Route path="pickups" element={<Pickups />} />
            <Route
              path="restaurants"
              element={
                <RoleRoute roles={['WORKSHOP_ADMIN']}>
                  <Restaurants />
                </RoleRoute>
              }
            />
            <Route
              path="accounts"
              element={
                <RoleRoute roles={['WORKSHOP_ADMIN']}>
                  <Accounts />
                </RoleRoute>
              }
            />
            <Route
              path="prices"
              element={
                <RoleRoute roles={['WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT']}>
                  <Prices />
                </RoleRoute>
              }
            />
            <Route
              path="finance"
              element={
                <RoleRoute roles={['WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'RESTAURANT_MANAGER']}>
                  <Finance />
                </RoleRoute>
              }
            />
            <Route path="profile" element={<Profile />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}
