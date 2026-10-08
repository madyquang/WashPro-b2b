import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Col, Empty, List, Row, Skeleton, Space, Typography } from 'antd'
import {
  CalendarOutlined,
  CarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  FileAddOutlined,
  ReloadOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import DueTag from '../components/DueTag'
import KpiCard from '../components/KpiCard'
import StatusTag from '../components/StatusTag'
import { getSession } from '../utils/auth'
import { groupByCategory } from '../utils/catalog'
import { formatCurrency, formatDateTime, getErrorMessage } from '../utils/format'
import { STATUS_META, STATUS_ORDER, type Dashboard as DashboardData, type Order, type OrderPage, type OrderStatus, type Role } from '../types'

/** Màu chấm cho từng trạng thái (khớp màu thẻ trạng thái của Ant Design). */
const STATUS_DOT: Record<OrderStatus, string> = {
  PENDING_CONFIRM: '#FAAD14',
  DISCREPANCY: '#F5222D',
  WASHING: '#1677FF',
  DELIVERING: '#13C2C2',
  DELIVERY_ISSUE: '#FA541C',
  COMPLETED: '#52C41A',
}

/** Số đơn tối đa trong khung "Việc cần bạn xử lý". */
const PENDING_LIMIT = 8

/** Những trạng thái đang chờ chính vai trò này xử lý. */
const ACTION_STATUSES: Record<Role, OrderStatus[]> = {
  DRIVER: ['DISCREPANCY', 'DELIVERING'],
  RESTAURANT_STAFF: ['PENDING_CONFIRM', 'DISCREPANCY', 'DELIVERING'],
  RESTAURANT_MANAGER: ['PENDING_CONFIRM', 'DISCREPANCY', 'DELIVERING'],
  WORKSHOP_ADMIN: ['DISCREPANCY', 'DELIVERY_ISSUE', 'WASHING', 'DELIVERING'],
  WORKSHOP_ACCOUNTANT: ['WASHING', 'DELIVERY_ISSUE'],
}

/** Lời nhắc và nút tắt trên băng chào, theo vai trò. */
const HERO: Record<Role, { text: string; actions: { label: string; to: string; icon: ReactNode; primary?: boolean }[] }> = {
  DRIVER: {
    text: 'Xem việc lấy đồ hôm nay và các đơn cần giao trả.',
    actions: [
      { label: 'Việc lấy đồ', to: '/pickups', icon: <CarOutlined />, primary: true },
      { label: 'Đơn cần giao', to: '/orders?status=DELIVERING', icon: <CheckCircleOutlined /> },
    ],
  },
  RESTAURANT_STAFF: {
    text: 'Cần xưởng đến lấy đồ? Gửi yêu cầu chỉ với vài thao tác.',
    actions: [
      { label: 'Yêu cầu lấy đồ', to: '/pickups', icon: <CarOutlined />, primary: true },
      { label: 'Đơn chờ xác nhận', to: '/orders?status=PENDING_CONFIRM', icon: <CheckCircleOutlined /> },
    ],
  },
  RESTAURANT_MANAGER: {
    text: 'Theo dõi đồ đang giặt, xác nhận số lượng và xem công nợ của bạn.',
    actions: [
      { label: 'Yêu cầu lấy đồ', to: '/pickups', icon: <CarOutlined />, primary: true },
      { label: 'Công nợ', to: '/finance', icon: <WalletOutlined /> },
    ],
  },
  WORKSHOP_ADMIN: {
    text: 'Điều phối lấy đồ, giặt và giao trả đúng hẹn cho mọi khách hàng.',
    actions: [
      { label: 'Phân công lấy đồ', to: '/pickups', icon: <CarOutlined />, primary: true },
      { label: 'Đơn trễ hạn', to: '/orders?overdue=1', icon: <WarningOutlined /> },
    ],
  },
  WORKSHOP_ACCOUNTANT: {
    text: 'Chốt giá các đơn đang giặt và theo dõi công nợ khách hàng.',
    actions: [
      { label: 'Công nợ', to: '/finance', icon: <WalletOutlined />, primary: true },
      { label: 'Đơn đang giặt', to: '/orders?status=WASHING', icon: <FileAddOutlined /> },
    ],
  },
}

const greeting = () => {
  const h = new Date().getHours()
  return h < 11 ? 'Chào buổi sáng' : h < 14 ? 'Chào buổi trưa' : h < 18 ? 'Chào buổi chiều' : 'Chào buổi tối'
}

export default function Dashboard() {
  const navigate = useNavigate()
  const session = getSession()
  const role = session?.role
  const [data, setData] = useState<DashboardData | null>(null)
  const [pending, setPending] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const wanted = role ? ACTION_STATUSES[role] : []
      // Chỉ lấy vài đơn cần xử lý (máy chủ đã lọc và xếp theo hạn trả), không tải toàn bộ đơn
      const top = (params: Record<string, unknown>) =>
        axiosClient.get<OrderPage>('/orders', { params: { sort: 'DUE', size: PENDING_LIMIT, ...params } }).then((r) => r.data.content)
      const [stats, byStatus, overdue] = await Promise.all([
        axiosClient.get<DashboardData>('/dashboard'),
        top({ status: wanted.join(',') }),
        role !== 'WORKSHOP_ACCOUNTANT' ? top({ overdue: true }) : Promise.resolve<Order[]>([]),
      ])
      setData(stats.data)
      // Gộp hai danh sách: đơn trễ hạn lên đầu, rồi theo hạn trả gần nhất
      const merged = new Map([...overdue, ...byStatus].map((o) => [o.id, o]))
      setPending(
        [...merged.values()]
          .sort((a, b) => Number(b.overdue) - Number(a.overdue) || (a.dueAt ?? '').localeCompare(b.dueAt ?? ''))
          .slice(0, PENDING_LIMIT),
      )
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được dữ liệu tổng quan'))
    } finally {
      setLoading(false)
    }
  }, [role])

  useEffect(() => {
    load()
  }, [load])

  if (!session || !role) return null
  const hero = HERO[role]
  const firstName = session.fullName.trim().split(/\s+/).pop()

  return (
    <>
      <section className="hero">
        <div>
          <h2>
            {greeting()}, {firstName} 👋
          </h2>
          <p>{hero.text}</p>
        </div>
        <Space wrap>
          {hero.actions.map((a) => (
            <Button key={a.to} type={a.primary ? 'primary' : 'default'} icon={a.icon} onClick={() => navigate(a.to)}>
              {a.label}
            </Button>
          ))}
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading} aria-label="Làm mới" />
        </Space>
      </section>

      {error && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={error} action={<Button size="small" onClick={load}>Thử lại</Button>} />}

      {loading && !data ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        data && (
          <>
            <Row gutter={[16, 16]}>
              <Col xs={12} lg={6}>
                <KpiCard
                  icon={<WarningOutlined />}
                  tone={data.overdueOrders > 0 ? 'red' : 'gray'}
                  label="Đơn trễ hạn trả"
                  value={data.overdueOrders}
                  valueColor={data.overdueOrders > 0 ? '#C2341B' : undefined}
                  to="/orders?overdue=1"
                />
              </Col>
              <Col xs={12} lg={6}>
                <KpiCard icon={<ClockCircleOutlined />} tone="orange" label="Hạn trả trong hôm nay" value={data.dueToday} />
              </Col>
              <Col xs={12} lg={6}>
                <KpiCard icon={<CarOutlined />} tone="teal" label="Yêu cầu lấy đồ đang chờ" value={data.openPickups} to="/pickups" />
              </Col>
              <Col xs={12} lg={6}>
                <KpiCard
                  icon={<CalendarOutlined />}
                  tone="navy"
                  label="Đơn nhận hôm nay"
                  value={data.ordersToday}
                  hint={`${data.totalOrders} đơn đang theo dõi`}
                />
              </Col>
            </Row>

            <Typography.Title level={5} style={{ margin: '24px 0 12px' }}>
              Đơn theo trạng thái
            </Typography.Title>
            <div className="pipeline">
              {STATUS_ORDER.map((s) => (
                <Link key={s} to={`/orders?status=${s}`} className="pipeline-step">
                  <div className="count">{data.countsByStatus[s] ?? 0}</div>
                  <div className="label">
                    <span className="dot" style={{ background: STATUS_DOT[s] }} />
                    {STATUS_META[s].label}
                  </div>
                </Link>
              ))}
            </div>

            <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
              <Col xs={24} xl={data.balance !== null ? 15 : 24}>
                <Card title="Việc cần bạn xử lý" extra={<Link to="/orders">Xem tất cả đơn</Link>} style={{ height: '100%' }}>
                  {pending.length === 0 ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có đơn nào cần xử lý 🎉" />
                  ) : (
                    <List
                      dataSource={pending}
                      renderItem={(o) => (
                        <List.Item
                          style={{ paddingInline: 0 }}
                          extra={
                            <Space size={4} wrap style={{ justifyContent: 'flex-end' }}>
                              {o.overdue && <DueTag order={o} />}
                              <StatusTag status={o.status} />
                            </Space>
                          }
                        >
                          <List.Item.Meta
                            title={
                              <Link to={`/orders?status=${o.status}`}>
                                #{o.id} · {o.restaurantName}
                              </Link>
                            }
                            description={[
                              `${o.totalBags} túi`,
                              groupByCategory(o.items).map((g) => g.label).join(', '),
                              o.dueAt ? `hạn trả ${formatDateTime(o.dueAt)}` : formatDateTime(o.createdAt),
                            ]
                              .filter(Boolean)
                              .join(' · ')}
                          />
                        </List.Item>
                      )}
                    />
                  )}
                </Card>
              </Col>

              {data.balance !== null && (
                <Col xs={24} xl={9}>
                  <Row gutter={[16, 16]}>
                    <Col xs={12} xl={24}>
                      <KpiCard icon={<DollarOutlined />} tone="navy" label="Tổng tiền đã chốt" value={formatCurrency(data.billed)} />
                    </Col>
                    <Col xs={12} xl={24}>
                      <KpiCard icon={<CheckCircleOutlined />} tone="green" label="Đã thanh toán" value={formatCurrency(data.paid)} />
                    </Col>
                    <Col xs={12} xl={24}>
                      <KpiCard icon={<WalletOutlined />} tone="teal" label="Công nợ còn lại" value={formatCurrency(data.balance)} to="/finance" />
                    </Col>
                    <Col xs={12} xl={24}>
                      <KpiCard
                        icon={<WarningOutlined />}
                        tone={(data.overdueDebt ?? 0) > 0 ? 'red' : 'gray'}
                        label="Nợ quá hạn thanh toán"
                        value={formatCurrency(data.overdueDebt)}
                        valueColor={(data.overdueDebt ?? 0) > 0 ? '#C2341B' : undefined}
                        to="/finance"
                      />
                    </Col>
                  </Row>
                </Col>
              )}
            </Row>
          </>
        )
      )}
    </>
  )
}
