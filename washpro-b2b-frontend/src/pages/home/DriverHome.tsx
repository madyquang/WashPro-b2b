import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Skeleton } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import EmptyState from '../../components/EmptyState'
import LoadError from '../../components/LoadError'
import PageHeader from '../../components/PageHeader'
import StatStrip from '../../components/StatStrip'
import { OrderCard } from '../orders/OrderCard'
import { orderHint } from '../orders/hints'
import { useOrderActions } from '../orders/useOrderActions'
import { CardGrid, Section } from './HomeParts'
import { greetingFor, todayLabel } from './greeting'
import { getSession } from '../../utils/auth'
import { getErrorMessage, todayIso } from '../../utils/format'
import type { Dashboard, Order, OrderPage } from '../../types'

/**
 * Trang chủ tài xế ("Hôm nay"): làm việc trên điện thoại, ngoài đường. Việc của tôi (đi lấy / đi giao) lên đầu với
 * nút Gọi / Chỉ đường / Đã nhận đồ / Giao xong; sau đó là việc đang chờ tài xế nhận ngay tại đây.
 */
export default function DriverHome() {
  const session = getSession()
  const [mine, setMine] = useState<Order[]>([])
  const [open, setOpen] = useState<OrderPage | null>(null)
  const [toDeliver, setToDeliver] = useState<OrderPage | null>(null)
  const [stats, setStats] = useState<Dashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!getSession()) return // đã đăng xuất: không gửi request không có token
    setLoading(true)
    setError(null)
    try {
      const list = (params: object, size: number) => axiosClient.get<OrderPage>('/orders', { params: { ...params, size } }).then((r) => r.data)
      const [m, o, w, d] = await Promise.all([
        list({ mine: true, sort: 'PICKUP' }, 50),
        list({ status: 'PICKUP_PENDING', sort: 'PICKUP' }, 3),
        list({ status: 'WASHED', sort: 'DUE' }, 3),
        axiosClient.get<Dashboard>('/dashboard'),
      ])
      setMine(m.content)
      setOpen(o)
      setToDeliver(w)
      setStats(d.data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được việc hôm nay'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const actions = useOrderActions(load)

  // Đã đăng xuất trong lúc trang còn đang tải (dữ liệu về muộn làm trang vẽ lại): không vẽ gì, tránh đọc phiên rỗng
  if (!session) return null

  const today = todayIso()
  const mineToday = mine.filter((o) => o.status === 'DELIVERING' || !o.preferredDate || o.preferredDate <= today).length
  const card = (o: Order) => (
    <OrderCard
      key={o.id}
      order={o}
      actions={actions.actionsFor(o)}
      busy={actions.busyId === o.id}
      onDetail={() => actions.openDetail(o.id)}
      hint={orderHint(o, 'DRIVER')}
    />
  )

  return (
    <>
      <PageHeader
        title={greetingFor(session.fullName)}
        description={todayLabel()}
        actions={<Button icon={<ReloadOutlined />} onClick={load} loading={loading} aria-label="Làm mới" />}
      />
      <LoadError error={error} onRetry={load} />

      {loading && !stats ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <>
          <div className="mb-4">
            <StatStrip
              aria-label="Việc hôm nay"
              items={[
                { key: 'mine', label: 'Việc cần làm hôm nay', value: mineToday, hint: `${mine.length} việc đã nhận`, tone: mineToday > 0 ? 'teal' : 'gray', to: '/pickups?tab=mine' },
                { key: 'open', label: 'Chờ tài xế lấy', value: open?.total ?? 0, tone: open?.total ? 'gold' : 'gray', emphasize: !!open?.total, to: '/pickups?tab=open' },
                { key: 'deliver', label: 'Chờ tài xế giao', value: toDeliver?.total ?? 0, tone: toDeliver?.total ? 'gold' : 'gray', emphasize: !!toDeliver?.total, to: '/pickups?tab=deliver' },
                { key: 'today', label: 'Đơn bạn nhận hôm nay', value: stats?.ordersToday ?? 0, to: '/orders' },
              ]}
            />
          </div>

          <Section title="Việc của tôi" count={mine.length} extra={mine.length > 0 && <Link to="/pickups?tab=mine">Xem tất cả</Link>}>
            {mine.length === 0 ? (
              <EmptyState description={open?.total || toDeliver?.total ? 'Bạn chưa nhận việc nào. Có việc đang chờ tài xế bên dưới.' : 'Bạn chưa nhận việc nào.'} />
            ) : (
              <CardGrid>{mine.map(card)}</CardGrid>
            )}
          </Section>

          {open && open.total > 0 && (
            <Section title="Chờ tài xế lấy" count={open.total} extra={open.total > open.content.length && <Link to="/pickups?tab=open">Xem tất cả</Link>}>
              <CardGrid>{open.content.map(card)}</CardGrid>
            </Section>
          )}

          {toDeliver && toDeliver.total > 0 && (
            <Section title="Giặt xong, chờ giao" count={toDeliver.total} extra={toDeliver.total > toDeliver.content.length && <Link to="/pickups?tab=deliver">Xem tất cả</Link>}>
              <CardGrid>{toDeliver.content.map(card)}</CardGrid>
            </Section>
          )}
        </>
      )}

      {actions.ui}
    </>
  )
}
