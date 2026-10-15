import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Skeleton } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import EmptyState from '../../components/EmptyState'
import LoadError from '../../components/LoadError'
import PageHeader from '../../components/PageHeader'
import StatStrip from '../../components/StatStrip'
import CreateOrderModal from '../orders/CreateOrderModal'
import { OrderCard } from '../orders/OrderCard'
import { orderHint } from '../orders/hints'
import { useOrderActions } from '../orders/useOrderActions'
import { usePickupActions } from '../pickups/usePickupActions'
import { CardGrid, Section } from './HomeParts'
import { greetingFor, todayLabel } from './greeting'
import { getSession } from '../../utils/auth'
import { getErrorMessage, todayIso } from '../../utils/format'
import type { Dashboard, Order, OrderPage, PickupRequest } from '../../types'

/**
 * Trang chủ tài xế ("Hôm nay"): làm việc trên điện thoại, ngoài đường. Việc đã nhận lên đầu với nút Gọi / Chỉ đường /
 * Tạo đơn; sau đó là việc đang chờ tài xế (nhận ngay tại đây) và đơn đang chờ khách xác nhận.
 */
export default function DriverHome() {
  const session = getSession()
  const [pickups, setPickups] = useState<PickupRequest[]>([])
  const [waiting, setWaiting] = useState<Order[]>([])
  const [stats, setStats] = useState<Dashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    if (!getSession()) return // đã đăng xuất: không gửi request không có token
    setLoading(true)
    setError(null)
    try {
      const [p, o, d] = await Promise.all([
        axiosClient.get<PickupRequest[]>('/pickups'),
        axiosClient.get<OrderPage>('/orders', { params: { status: 'PENDING_CONFIRM,DISCREPANCY', sort: 'DUE', size: 6 } }),
        axiosClient.get<Dashboard>('/dashboard'),
      ])
      setPickups(p.data)
      setWaiting(o.data.content)
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

  const pickupActions = usePickupActions(load)
  const orderActions = useOrderActions(load)

  // Đã đăng xuất trong lúc trang còn đang tải (dữ liệu về muộn làm trang vẽ lại): không vẽ gì, tránh đọc phiên rỗng
  if (!session) return null

  const byDate = (a: PickupRequest, b: PickupRequest) => a.preferredDate.localeCompare(b.preferredDate) || a.id - b.id
  const mine = pickups.filter((p) => p.status === 'ACCEPTED').sort(byDate)
  const open = pickups.filter((p) => p.status === 'OPEN').sort(byDate)
  const today = todayIso()
  const mineToday = mine.filter((p) => p.preferredDate <= today).length

  return (
    <>
      <PageHeader
        title={greetingFor(session.fullName)}
        description={todayLabel()}
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading} aria-label="Làm mới" />
            <Button icon={<PlusOutlined />} onClick={() => setCreating(true)}>
              Tạo đơn không theo yêu cầu
            </Button>
          </>
        }
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
                { key: 'mine', label: 'Cần đi lấy hôm nay', value: mineToday, hint: `${mine.length} việc đã nhận`, tone: mineToday > 0 ? 'teal' : 'gray', to: '/pickups?tab=mine' },
                { key: 'open', label: 'Đang chờ tài xế', value: open.length, tone: open.length > 0 ? 'gold' : 'gray', emphasize: open.length > 0, to: '/pickups?tab=open' },
                { key: 'today', label: 'Đơn bạn tạo hôm nay', value: stats?.ordersToday ?? 0, to: '/orders' },
              ]}
            />
          </div>

          <Section title="Việc của tôi" count={mine.length} extra={mine.length > 0 && <Link to="/pickups?tab=mine">Xem tất cả</Link>}>
            {mine.length === 0 ? (
              <EmptyState
                description={open.length > 0 ? 'Bạn chưa nhận việc nào. Có yêu cầu đang chờ tài xế bên dưới.' : 'Bạn chưa nhận việc nào.'}
              />
            ) : (
              <CardGrid>{mine.map((p) => pickupActions.cardFor(p))}</CardGrid>
            )}
          </Section>

          {open.length > 0 && (
            <Section title="Đang chờ tài xế nhận" count={open.length} extra={open.length > 3 && <Link to="/pickups?tab=open">Xem tất cả</Link>}>
              <CardGrid>{open.slice(0, 3).map((p) => pickupActions.cardFor(p))}</CardGrid>
            </Section>
          )}

          <Section title="Đơn chờ khách xác nhận" extra={<Link to="/orders?view=waiting">Xem tất cả</Link>}>
            {waiting.length === 0 ? (
              <EmptyState compact description="Không có đơn nào đang chờ khách xác nhận." />
            ) : (
              <CardGrid>
                {waiting.map((o) => (
                  <OrderCard
                    key={o.id}
                    order={o}
                    actions={orderActions.actionsFor(o)}
                    busy={orderActions.busyId === o.id}
                    onDetail={() => orderActions.openDetail(o.id)}
                    hint={orderHint(o, 'DRIVER')}
                  />
                ))}
              </CardGrid>
            )}
          </Section>
        </>
      )}

      <CreateOrderModal
        open={creating}
        onClose={() => setCreating(false)}
        onDone={() => {
          setCreating(false)
          load()
        }}
      />
      {pickupActions.ui}
      {orderActions.ui}
    </>
  )
}
