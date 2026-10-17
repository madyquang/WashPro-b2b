import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button, Card, Col, Grid, Row, Skeleton } from 'antd'
import {
  CalendarOutlined,
  CarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  InboxOutlined,
  ReloadOutlined,
  UserAddOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import EmptyState from '../../components/EmptyState'
import LoadError from '../../components/LoadError'
import PageHeader from '../../components/PageHeader'
import StatStrip, { type Stat } from '../../components/StatStrip'
import { STATUS_TONES, type StatusTone } from '../../theme'
import { OrderCard, OrderRow } from '../orders/OrderCard'
import { orderHint } from '../orders/hints'
import { useOrderActions } from '../orders/useOrderActions'
import { CardGrid, Section, StatusStrip } from './HomeParts'
import { greetingFor, todayLabel } from './greeting'
import { getSession } from '../../utils/auth'
import { formatCurrency, getErrorMessage } from '../../utils/format'
import type { Dashboard, Order, OrderPage } from '../../types'

/** Số việc tối đa trong khung "Đơn cần xử lý". */
const ATTENTION_LIMIT = 8

interface Chip {
  key: string
  count: number
  label: string
  tone: StatusTone
  icon: ReactNode
  to: string
}

/**
 * Trang chủ Quản lý xưởng (bảng điều phối, dùng trên máy tính): việc tồn đọng lên đầu thành các chip bấm được,
 * đơn cần xử lý (bắt đầu giặt, giặt xong, phân công giao, sự cố) có nút làm ngay, yêu cầu chưa có tài xế phân công
 * ngay tại đây.
 */
export default function AdminHome() {
  const session = getSession()
  const navigate = useNavigate()
  const screens = Grid.useBreakpoint()
  const [data, setData] = useState<Dashboard | null>(null)
  const [pickups, setPickups] = useState<Order[]>([])
  const [attention, setAttention] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!getSession()) return // đã đăng xuất: không gửi request không có token
    setLoading(true)
    setError(null)
    try {
      const top = (params: Record<string, unknown>) =>
        axiosClient.get<OrderPage>('/orders', { params: { sort: 'DUE', size: ATTENTION_LIMIT, ...params } }).then((r) => r.data.content)
      const [d, p, byStatus, overdue] = await Promise.all([
        axiosClient.get<Dashboard>('/dashboard'),
        axiosClient.get<OrderPage>('/orders', { params: { status: 'PICKUP_PENDING,PICKUP_ASSIGNED', sort: 'PICKUP', size: 100 } }),
        top({ status: 'RECEIVED,WASHED,DELIVERY_ISSUE,WASHING,DELIVERING' }),
        top({ overdue: true }),
      ])
      setData(d.data)
      setPickups(p.data.content)
      // Gộp hai danh sách: đơn trễ hạn lên đầu, rồi theo hạn trả gần nhất
      const merged = new Map([...overdue, ...byStatus].map((o) => [o.id, o]))
      setAttention(
        [...merged.values()]
          .sort((a, b) => Number(b.overdue) - Number(a.overdue) || (a.dueAt ?? '').localeCompare(b.dueAt ?? ''))
          .slice(0, ATTENTION_LIMIT),
      )
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được bảng điều phối'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const orderActions = useOrderActions(load)

  // Đã đăng xuất trong lúc trang còn đang tải (dữ liệu về muộn làm trang vẽ lại): không vẽ gì, tránh đọc phiên rỗng
  if (!session) return null

  const header = (
    <PageHeader
      title={greetingFor(session.fullName)}
      description={`${todayLabel()} · Điều phối lấy đồ, giặt và giao trả đúng hẹn.`}
      actions={
        <>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading} aria-label="Làm mới" />
          <Button icon={<UserAddOutlined />} onClick={orderActions.openCreate}>
            Tạo yêu cầu hộ khách
          </Button>
          <Button type="primary" icon={<CarOutlined />} onClick={() => navigate('/pickups')}>
            Phân công lấy đồ
          </Button>
        </>
      }
    />
  )

  if (!data) {
    return (
      <>
        {header}
        <LoadError error={error} onRetry={load} />
        {loading && <Skeleton active paragraph={{ rows: 10 }} />}
        {orderActions.ui}
      </>
    )
  }

  const unassigned = pickups.filter((p) => p.status === 'PICKUP_PENDING')
  const latePickups = pickups.filter((p) => p.pickupLate).length
  const counts = data.countsByStatus

  const chips: Chip[] = [
    { key: 'overdue', count: data.overdueOrders, label: 'đơn trễ hạn trả', tone: 'red', icon: <WarningOutlined />, to: '/orders?overdue=1' },
    { key: 'unassigned', count: unassigned.length, label: 'yêu cầu chưa có tài xế', tone: 'gold', icon: <CarOutlined />, to: '/pickups?tab=open' },
    { key: 'latePickup', count: latePickups, label: 'việc lấy đồ quá ngày hẹn', tone: 'orange', icon: <ClockCircleOutlined />, to: '/pickups?tab=open' },
    { key: 'received', count: counts.RECEIVED ?? 0, label: 'đơn đang về xưởng, chờ bắt đầu giặt', tone: 'purple', icon: <InboxOutlined />, to: '/orders?status=RECEIVED' },
    { key: 'washed', count: counts.WASHED ?? 0, label: 'đơn giặt xong chờ giao', tone: 'teal', icon: <ExclamationCircleOutlined />, to: '/pickups?tab=deliver' },
    { key: 'issue', count: counts.DELIVERY_ISSUE ?? 0, label: 'sự cố giao trả', tone: 'orange', icon: <WarningOutlined />, to: '/orders?status=DELIVERY_ISSUE' },
  ].filter((c) => c.count > 0) as Chip[]

  const kpis: Stat[] = [
    { key: 'dueToday', icon: <ClockCircleOutlined />, tone: 'orange', label: 'Hạn trả trong hôm nay', value: data.dueToday, to: '/orders?status=WASHING' },
    { key: 'washing', icon: <ExclamationCircleOutlined />, tone: 'blue', label: 'Đang giặt', value: counts.WASHING ?? 0, to: '/orders?status=WASHING' },
    { key: 'delivering', icon: <CarOutlined />, tone: 'teal', label: 'Đang giao trả', value: counts.DELIVERING ?? 0, to: '/pickups?tab=delivering' },
    { key: 'today', icon: <CalendarOutlined />, tone: 'navy', label: 'Đơn nhận hôm nay', value: data.ordersToday, hint: `${data.totalOrders} đơn đang theo dõi` },
  ]

  const overdueDebt = data.overdueDebt ?? 0
  const finance: Stat[] = [
    { key: 'billed', icon: <DollarOutlined />, tone: 'navy', label: 'Tổng tiền đã chốt', value: formatCurrency(data.billed) },
    { key: 'paid', icon: <CheckCircleOutlined />, tone: 'green', label: 'Đã thanh toán', value: formatCurrency(data.paid) },
    { key: 'balance', icon: <WalletOutlined />, tone: 'teal', label: 'Công nợ còn lại', value: formatCurrency(data.balance), to: '/finance' },
    {
      key: 'overdueDebt',
      icon: <WarningOutlined />,
      tone: overdueDebt > 0 ? 'red' : 'gray',
      emphasize: overdueDebt > 0,
      label: 'Nợ quá hạn thanh toán',
      value: formatCurrency(data.overdueDebt),
      to: '/finance',
    },
  ]

  const desktop = screens.md !== false

  return (
    <>
      {header}
      <LoadError error={error} onRetry={load} />

      {/* Việc tồn đọng: mỗi chip mở đúng danh sách cần xử lý */}
      <nav className="attention-bar" aria-label="Việc cần chú ý">
        {chips.length === 0 ? (
          <span className="attention-chip" style={{ background: STATUS_TONES.green.bg, color: STATUS_TONES.green.fg }}>
            <CheckCircleOutlined aria-hidden /> Mọi việc đang đúng tiến độ
          </span>
        ) : (
          chips.map((c) => (
            <Link key={c.key} to={c.to} className="attention-chip" style={{ background: STATUS_TONES[c.tone].bg, color: STATUS_TONES[c.tone].fg }}>
              <span aria-hidden>{c.icon}</span>
              {c.count} {c.label}
            </Link>
          ))
        )}
      </nav>

      <StatStrip items={kpis} aria-label="Vận hành hôm nay" />

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} xl={15}>
          <Section title="Đơn cần xử lý" extra={<Link to="/orders">Tất cả đơn</Link>}>
            {attention.length === 0 ? (
              <EmptyState compact description="Không có đơn nào cần xử lý lúc này." />
            ) : desktop ? (
              <Card styles={{ body: { padding: 0 } }}>
                {attention.map((o) => (
                  <OrderRow
                    key={o.id}
                    order={o}
                    actions={orderActions.actionsFor(o)}
                    busy={orderActions.busyId === o.id}
                    onDetail={() => orderActions.openDetail(o.id)}
                    onMenuAction={orderActions.runFromMenu}
                  />
                ))}
              </Card>
            ) : (
              // Điện thoại: 4 đơn đầu (đã xếp trễ hạn trước), phần còn lại ở trang Đơn hàng
              <CardGrid>
                {attention.slice(0, 4).map((o) => (
                  <OrderCard
                    key={o.id}
                    order={o}
                    actions={orderActions.actionsFor(o)}
                    busy={orderActions.busyId === o.id}
                    onDetail={() => orderActions.openDetail(o.id)}
                    showMoney
                    hint={orderHint(o, 'WORKSHOP_ADMIN')}
                    onMenuAction={orderActions.runFromMenu}
                  />
                ))}
              </CardGrid>
            )}
          </Section>
        </Col>
        <Col xs={24} xl={9}>
          <Section title="Chưa có tài xế" count={unassigned.length} extra={unassigned.length > 2 && <Link to="/pickups?tab=open">Xem tất cả</Link>}>
            {unassigned.length === 0 ? (
              <EmptyState compact description="Mọi yêu cầu lấy đồ đều đã có tài xế." />
            ) : (
              <div className="card-stack">
                {unassigned.slice(0, 2).map((o) => (
                  <OrderCard
                    key={o.id}
                    order={o}
                    actions={orderActions.actionsFor(o)}
                    busy={orderActions.busyId === o.id}
                    onDetail={() => orderActions.openDetail(o.id)}
                    onMenuAction={orderActions.runFromMenu}
                  />
                ))}
              </div>
            )}
          </Section>
        </Col>
      </Row>

      <Section title="Đơn theo trạng thái">
        <StatusStrip counts={data.countsByStatus} linkFor={(s) => `/orders?status=${s}`} />
      </Section>

      <Section title="Tài chính" extra={<Link to="/finance">Công nợ & thanh toán</Link>}>
        <StatStrip items={finance} aria-label="Tóm tắt tài chính" />
      </Section>

      {orderActions.ui}
    </>
  )
}
