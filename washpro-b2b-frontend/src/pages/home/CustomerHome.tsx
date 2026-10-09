import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Skeleton, Space, Typography } from 'antd'
import { CarOutlined, FileTextOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import DueTag from '../../components/DueTag'
import EmptyState from '../../components/EmptyState'
import LoadError from '../../components/LoadError'
import PageHeader from '../../components/PageHeader'
import StatStrip from '../../components/StatStrip'
import StatusTag from '../../components/StatusTag'
import StatementModal from '../finance/StatementModal'
import { OrderCard } from '../orders/OrderCard'
import { orderHint } from '../orders/hints'
import { useOrderActions } from '../orders/useOrderActions'
import { isActivePickup, usePickupActions } from '../pickups/usePickupActions'
import { CardGrid, Section } from './HomeParts'
import { greetingFor } from './greeting'
import { getSession } from '../../utils/auth'
import { groupByCategory } from '../../utils/catalog'
import { formatCurrency, formatDateTime, getErrorMessage } from '../../utils/format'
import type { DebtRow, Order, OrderPage, PickupRequest } from '../../types'

/**
 * Trang chủ phía khách (nhân viên + quản lý): một nút gửi yêu cầu lấy đồ, các đơn cần bạn xác nhận với nút làm ngay,
 * đồ đang ở xưởng và (chỉ quản lý) công nợ + sao kê. Không có số liệu vận hành của xưởng.
 */
export default function CustomerHome() {
  const session = getSession()!
  const isManager = session.role === 'RESTAURANT_MANAGER'
  const [pickups, setPickups] = useState<PickupRequest[]>([])
  const [todo, setTodo] = useState<Order[]>([])
  const [todoTotal, setTodoTotal] = useState(0)
  const [atWorkshop, setAtWorkshop] = useState<Order[]>([])
  const [atWorkshopTotal, setAtWorkshopTotal] = useState(0)
  const [debt, setDebt] = useState<DebtRow | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statementOpen, setStatementOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const orders = (status: string, size: number) =>
        axiosClient.get<OrderPage>('/orders', { params: { status, sort: 'DUE', size } }).then((r) => r.data)
      const [p, t, w, d] = await Promise.all([
        axiosClient.get<PickupRequest[]>('/pickups'),
        orders('PENDING_CONFIRM,DISCREPANCY,DELIVERING', 10),
        orders('WASHING,DELIVERY_ISSUE', 5),
        // Công nợ chỉ quản lý phía khách được xem; máy chủ trả về đúng một dòng của khách mình
        isManager ? axiosClient.get<DebtRow[]>('/reports/debts').then((r) => r.data[0] ?? null) : Promise.resolve(null),
      ])
      setPickups(p.data)
      setTodo(t.content)
      setTodoTotal(t.total)
      setAtWorkshop(w.content)
      setAtWorkshopTotal(w.total)
      setDebt(d)
      setLoaded(true)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được trang chủ'))
    } finally {
      setLoading(false)
    }
  }, [isManager])

  useEffect(() => {
    load()
  }, [load])

  const pickupActions = usePickupActions(load)
  const orderActions = useOrderActions(load)
  const activePickups = pickups.filter(isActivePickup).sort((a, b) => a.preferredDate.localeCompare(b.preferredDate))

  return (
    <>
      <PageHeader
        title={greetingFor(session.fullName)}
        description={`${session.restaurantName ?? ''} · Cần xưởng đến lấy đồ? Gửi yêu cầu chỉ với vài thao tác.`}
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading} aria-label="Làm mới" />
            <Button type="primary" icon={<CarOutlined />} onClick={pickupActions.openCreate}>
              Yêu cầu lấy đồ
            </Button>
          </>
        }
      />
      <LoadError error={error} onRetry={load} />

      {!loaded ? (
        loading && <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <>
          <Section title="Cần bạn xác nhận" count={todoTotal} extra={todoTotal > todo.length && <Link to="/orders?view=todo">Xem tất cả</Link>}>
            {todo.length === 0 ? (
              <EmptyState compact description="Không có đơn nào cần bạn xác nhận lúc này." />
            ) : (
              <CardGrid>
                {todo.map((o) => (
                  <OrderCard
                    key={o.id}
                    order={o}
                    actions={orderActions.actionsFor(o)}
                    busy={orderActions.busyId === o.id}
                    onDetail={() => orderActions.openDetail(o.id)}
                    showMoney={isManager}
                    hideCustomer
                    hint={orderHint(o, session.role)}
                  />
                ))}
              </CardGrid>
            )}
          </Section>

          {activePickups.length > 0 && (
            <Section title="Yêu cầu lấy đồ đang chờ" count={activePickups.length} extra={<Link to="/pickups">Xem tất cả</Link>}>
              <CardGrid>{activePickups.slice(0, 4).map((p) => pickupActions.cardFor(p))}</CardGrid>
            </Section>
          )}

          <Section title="Đồ đang ở xưởng" count={atWorkshopTotal} extra={atWorkshopTotal > 0 && <Link to="/orders?view=washing">Xem tất cả</Link>}>
            {atWorkshop.length === 0 ? (
              <EmptyState compact description="Không có đồ nào đang ở xưởng." />
            ) : (
              <div className="mini-list">
                {atWorkshop.map((o) => (
                  <button key={o.id} type="button" className="mini-list-item mini-list-button" onClick={() => orderActions.openDetail(o.id)}>
                    <span style={{ minWidth: 0, textAlign: 'left' }}>
                      <Typography.Text strong>Đơn #{o.id}</Typography.Text>
                      <span className="order-card-meta" style={{ display: 'block' }}>
                        {o.totalBags} túi · {groupByCategory(o.items).map((g) => g.label).join(', ')}
                        {o.dueAt && ` · hẹn trả ${formatDateTime(o.dueAt)}`}
                      </span>
                    </span>
                    <Space size={4} wrap style={{ justifyContent: 'flex-end' }}>
                      {o.overdue && <DueTag order={o} />}
                      <StatusTag status={o.status} />
                    </Space>
                  </button>
                ))}
              </div>
            )}
          </Section>

          {isManager && debt && (
            <Section
              title="Công nợ với xưởng"
              extra={
                <Space size={8} wrap>
                  <Button icon={<FileTextOutlined />} onClick={() => setStatementOpen(true)}>
                    Sao kê tháng
                  </Button>
                  <Link to="/finance">Chi tiết</Link>
                </Space>
              }
            >
              <StatStrip
                aria-label="Công nợ"
                items={[
                  { key: 'balance', label: 'Còn nợ', value: formatCurrency(debt.balance), tone: 'teal' },
                  {
                    key: 'overdue',
                    label: 'Đã quá hạn thanh toán',
                    value: formatCurrency(debt.overdueTotal),
                    tone: debt.overdueTotal > 0 ? 'red' : 'gray',
                    emphasize: debt.overdueTotal > 0,
                  },
                  { key: 'paid', label: 'Đã thanh toán', value: formatCurrency(debt.paid), tone: 'green' },
                ]}
              />
            </Section>
          )}
        </>
      )}

      {isManager && <StatementModal customer={statementOpen ? debt : null} onClose={() => setStatementOpen(false)} />}
      {pickupActions.ui}
      {orderActions.ui}
    </>
  )
}
