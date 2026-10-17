import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import {
  AppstoreOutlined,
  DownloadOutlined,
  AuditOutlined,
  BarChartOutlined,
  CarOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  NodeIndexOutlined,
  ShopOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import logo from '../assets/logo-mark.png'
import { BRAND } from '../theme'
import NotificationBell from './NotificationBell'
import ToneTag from './ToneTag'
import TouchFriendly from './TouchFriendly'
import { useInstallPrompt, useOnline } from '../utils/pwa'
import { forgetThisDevice, syncPush } from '../utils/push'
import { clearSession, canManagePrices, canViewFinance, getSession, isCustomerRole } from '../utils/auth'
import { ROLE_LABELS, type Role } from '../types'

const { Sider, Header, Content } = Layout

interface NavItem {
  key: string
  icon: ReactNode
  label: string
  /** Nhãn ngắn cho thanh điều hướng dưới cùng trên điện thoại. */
  short?: string
}

interface NavGroup {
  title: string
  items: NavItem[]
}

/** Menu theo vai trò, chia nhóm cho dễ tìm. */
function navFor(role: Role): NavGroup[] {
  // Nhãn theo cách mỗi vai trò gọi công việc của mình (khớp tiêu đề trang tương ứng)
  const driver = role === 'DRIVER'
  const customer = isCustomerRole(role)
  const ops: NavItem[] = driver
    ? [
        { key: '/', icon: <HomeOutlined />, label: 'Việc hôm nay', short: 'Hôm nay' },
        { key: '/pickups', icon: <CarOutlined />, label: 'Việc lấy đồ', short: 'Lấy đồ' },
        { key: '/orders', icon: <FileTextOutlined />, label: 'Đơn đã tạo', short: 'Đơn đã tạo' },
        { key: '/route', icon: <NodeIndexOutlined />, label: 'Lộ trình', short: 'Lộ trình' },
      ]
    : customer
      ? [
          { key: '/', icon: <HomeOutlined />, label: 'Trang chủ', short: 'Trang chủ' },
          { key: '/pickups', icon: <CarOutlined />, label: 'Yêu cầu lấy đồ', short: 'Lấy đồ' },
          { key: '/orders', icon: <FileTextOutlined />, label: 'Đơn giặt', short: 'Đơn giặt' },
        ]
      : [
          { key: '/', icon: <AppstoreOutlined />, label: role === 'WORKSHOP_ADMIN' ? 'Điều phối' : 'Tổng quan', short: 'Tổng quan' },
          { key: '/pickups', icon: <CarOutlined />, label: 'Lấy đồ', short: 'Lấy đồ' },
          { key: '/orders', icon: <FileTextOutlined />, label: 'Đơn hàng', short: 'Đơn hàng' },
        ]
  const manage: NavItem[] = []
  if (role === 'WORKSHOP_ADMIN') manage.push({ key: '/customers', icon: <ShopOutlined />, label: 'Khách hàng' })
  if (canManagePrices(role)) manage.push({ key: '/prices', icon: <TagsOutlined />, label: 'Bảng giá' })
  if (role === 'WORKSHOP_ADMIN') manage.push({ key: '/accounts', icon: <TeamOutlined />, label: 'Tài khoản' })
  if (role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT') manage.push({ key: '/audit', icon: <AuditOutlined />, label: 'Nhật ký thay đổi' })
  const finance: NavItem[] = canViewFinance(role)
    ? [
        { key: '/finance', icon: <DollarOutlined />, label: customer ? 'Công nợ' : 'Công nợ & thanh toán', short: 'Công nợ' },
        { key: '/performance', icon: <BarChartOutlined />, label: customer ? 'Chất lượng dịch vụ' : 'Hiệu suất' },
      ]
    : []

  return [
    { title: driver || customer ? 'Công việc' : 'Vận hành', items: ops },
    { title: 'Quản lý', items: manage },
    { title: 'Tài chính & báo cáo', items: finance },
  ].filter((g) => g.items.length > 0)
}

/** Chữ cái đầu của tên để làm ảnh đại diện. */
const initials = (name: string) =>
  name
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((w) => w[0]?.toUpperCase() ?? '')
    .join('')

export default function MainLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const screens = Grid.useBreakpoint()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const session = getSession()
  const contentRef = useRef<HTMLElement>(null)
  const firstRender = useRef(true)
  const online = useOnline()
  // Thiết bị đã bật thông báo đẩy: gắn đăng ký với tài khoản vừa đăng nhập
  useEffect(() => {
    void syncPush()
  }, [])
  const { canInstall, install } = useInstallPrompt()

  // Đổi trang: đưa focus về vùng nội dung để trình đọc màn hình / bàn phím bắt đầu từ trang mới
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    contentRef.current?.focus({ preventScroll: true })
    window.scrollTo(0, 0)
  }, [location.pathname])

  if (!session) return null // ProtectedRoute sẽ chuyển hướng về trang đăng nhập
  const { role } = session
  const isDesktop = !!screens.lg
  // Tài xế và khách hàng chủ yếu dùng điện thoại: điều hướng dưới cùng, bấm một tay
  const fieldRole = role === 'DRIVER' || isCustomerRole(role)
  const useBottomNav = !isDesktop && fieldRole

  const groups = navFor(role)
  const allItems = groups.flatMap((g) => g.items)
  const firstSegment = '/' + (location.pathname.split('/')[1] ?? '')
  const selectedKey = allItems.some((i) => i.key === firstSegment) ? firstSegment : location.pathname === '/profile' ? '/profile' : '/'

  const menuItems: MenuProps['items'] = groups.map((g) => ({
    type: 'group' as const,
    key: g.title,
    label: g.title,
    children: g.items.map((i) => ({ key: i.key, icon: i.icon, label: i.label })),
  }))

  const go = (key: string) => {
    navigate(key)
    setDrawerOpen(false)
  }

  const logout = async () => {
    await forgetThisDevice()
    clearSession()
    navigate('/login', { replace: true })
  }

  const userMenu: MenuProps['items'] = [
    ...(canInstall
      ? [{ key: 'install', icon: <DownloadOutlined />, label: 'Cài ứng dụng lên máy', onClick: () => void install() }, { type: 'divider' as const }]
      : []),
    {
      key: 'who',
      disabled: true,
      label: (
        <div style={{ lineHeight: 1.4 }}>
          <div style={{ fontWeight: 600 }}>{session.fullName}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {ROLE_LABELS[role]}
          </Typography.Text>
        </div>
      ),
    },
    { type: 'divider' },
    { key: 'profile', icon: <UserOutlined />, label: 'Thông tin tài khoản', onClick: () => navigate('/profile') },
    { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true, onClick: logout },
  ]

  const brand = (
    <div className="brand">
      <div className="brand-logo" aria-hidden>
        <img src={logo} alt="" />
      </div>
      <div>
        <div className="brand-name">{BRAND.name}</div>
        <div className="brand-tagline">{BRAND.tagline}</div>
      </div>
    </div>
  )

  const sideMenu = (
    <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={menuItems} onClick={({ key }) => go(key)} />
  )

  // Thanh dưới cùng: các mục chính + Tài khoản
  const bottomItems: NavItem[] = [
    ...allItems.filter((i) => i.short),
    { key: '/profile', icon: <UserOutlined />, label: 'Tài khoản', short: 'Tài khoản' },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }} className={useBottomNav ? 'with-bottom-nav' : undefined}>
      <a href="#main-content" className="skip-link">
        Bỏ qua tới nội dung
      </a>
      {isDesktop && (
        <Sider width={248} className="app-sider">
          {brand}
          {sideMenu}
        </Sider>
      )}

      <Layout>
        <Header className="app-header">
          <div className="header-left">
            {!isDesktop && !useBottomNav && (
              <Button type="text" icon={<MenuOutlined />} aria-label="Mở menu" onClick={() => setDrawerOpen(true)} />
            )}
            {!isDesktop && <img src={logo} alt="" width={30} height={30} style={{ objectFit: 'contain' }} />}
            <div className="header-context">
              {session.customerName ?? `Xưởng giặt ${BRAND.name}`}
              <small>
                {new Date().toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' })}
              </small>
            </div>
          </div>
          <div className="header-right">
          <NotificationBell />
          <Dropdown menu={{ items: userMenu }} trigger={['click']} placement="bottomRight">
            <Button type="text" className="user-button">
              <Space size={8}>
                <Avatar size={32} className="user-avatar">
                  {initials(session.fullName) || <UserOutlined />}
                </Avatar>
                <span className="user-name">{session.fullName}</span>
                <span className="role-tag">
                  <ToneTag tone="teal">{ROLE_LABELS[role]}</ToneTag>
                </span>
              </Space>
            </Button>
          </Dropdown>
          </div>
        </Header>

        {!online && (
          <div className="offline-banner" role="status">
            Mất kết nối mạng. Vẫn xem được trang đã mở; thao tác (gửi, xác nhận, lưu) sẽ chưa thực hiện được cho tới khi có mạng lại.
          </div>
        )}
        <Content className="app-content" id="main-content" ref={contentRef} tabIndex={-1}>
          <TouchFriendly>
            <Outlet />
          </TouchFriendly>
        </Content>
      </Layout>

      {!isDesktop && !useBottomNav && (
        <Drawer
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          size={270}
          closable={false}
          className="mobile-drawer"
        >
          {brand}
          {sideMenu}
        </Drawer>
      )}

      {useBottomNav && (
        <nav className="bottom-nav" aria-label="Điều hướng chính">
          {bottomItems.map((i) => (
            <button
              key={i.key}
              type="button"
              className={selectedKey === i.key ? 'active' : undefined}
              aria-current={selectedKey === i.key ? 'page' : undefined}
              onClick={() => go(i.key)}
            >
              {i.icon}
              <span>{i.short}</span>
            </button>
          ))}
        </nav>
      )}
    </Layout>
  )
}
