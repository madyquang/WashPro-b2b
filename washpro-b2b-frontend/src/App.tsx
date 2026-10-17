import { lazy, Suspense, useEffect, type ReactElement } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { App as AntApp, ConfigProvider, Spin } from 'antd'
import viVN from 'antd/locale/vi_VN'
import MainLayout from './components/MainLayout'
import PageErrorBoundary from './components/PageErrorBoundary'
import Login from './pages/Login'
import { antdTheme } from './theme'
import { bindMessage } from './utils/message'

// Mỗi trang là một gói JS riêng, chỉ tải khi người dùng mở trang đó
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Orders = lazy(() => import('./pages/Orders'))
const Pickups = lazy(() => import('./pages/Pickups'))
const Customers = lazy(() => import('./pages/Customers'))
const Accounts = lazy(() => import('./pages/Accounts'))
const Prices = lazy(() => import('./pages/Prices'))
const Finance = lazy(() => import('./pages/Finance'))
const Performance = lazy(() => import('./pages/Performance'))
const RoutePlan = lazy(() => import('./pages/RoutePlan'))
const AuditLog = lazy(() => import('./pages/AuditLog'))
const Profile = lazy(() => import('./pages/Profile'))
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

/** Vùng nội dung khi đang tải gói JS của trang (khung menu vẫn giữ nguyên). */
function PageSuspense() {
  const { pathname } = useLocation()
  return (
    <PageErrorBoundary key={pathname}>
      <Suspense fallback={<Spin size="large" style={{ display: 'block', margin: '96px auto' }} />}>
        <Outlet />
      </Suspense>
    </PageErrorBoundary>
  )
}

/** Gắn instance message theo theme (App.useApp) cho utils/message để mọi toast dùng đúng màu của ứng dụng. */
function MessageBridge() {
  const { message } = AntApp.useApp()
  useEffect(() => bindMessage(message), [message])
  return null
}

export default function App() {
  return (
    <ConfigProvider locale={viVN} theme={antdTheme}>
      <AntApp>
        <MessageBridge />
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
              <Route element={<PageSuspense />}>
                <Route index element={<Dashboard />} />
                <Route path="orders" element={<Orders />} />
                <Route path="pickups" element={<Pickups />} />
                <Route
                  path="customers"
                  element={
                    <RoleRoute roles={['WORKSHOP_ADMIN']}>
                      <Customers />
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
                    <RoleRoute roles={['WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER']}>
                      <Finance />
                    </RoleRoute>
                  }
                />
                <Route
                  path="performance"
                  element={
                    <RoleRoute roles={['WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT', 'CUSTOMER_MANAGER']}>
                      <Performance />
                    </RoleRoute>
                  }
                />
                <Route
                  path="route"
                  element={
                    <RoleRoute roles={['DRIVER']}>
                      <RoutePlan />
                    </RoleRoute>
                  }
                />
                <Route
                  path="audit"
                  element={
                    <RoleRoute roles={['WORKSHOP_ADMIN', 'WORKSHOP_ACCOUNTANT']}>
                      <AuditLog />
                    </RoleRoute>
                  }
                />
                <Route path="profile" element={<Profile />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  )
}
