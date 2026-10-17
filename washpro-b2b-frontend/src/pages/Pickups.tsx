import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button, Skeleton, Tabs } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import EmptyState from '../components/EmptyState'
import PageHeader from '../components/PageHeader'
import { OrderCard } from './orders/OrderCard'
import { orderHint } from './orders/hints'
import { useOrderActions } from './orders/useOrderActions'
import { CardGrid } from './home/HomeParts'
import { getSession, isCustomerRole } from '../utils/auth'
import { getErrorMessage } from '../utils/format'
import type { Order, OrderPage, OrderStatus, Role } from '../types'

interface JobTab {
  key: string
  label: string
  /** Tham số lọc danh sách đơn. */
  params: { status?: string; mine?: boolean; cancelled?: boolean; sort?: string }
  /** Số trên tab lấy từ số đếm theo trạng thái (tab "việc của tôi" đếm riêng; tab đã hủy không đếm). */
  countOf?: OrderStatus[]
  empty: string
}

/** Tab theo việc của từng vai trò: tài xế "việc của tôi" trước, xưởng "chưa có tài xế" trước. */
function tabsFor(role: Role): JobTab[] {
  if (role === 'DRIVER') {
    return [
      { key: 'mine', label: 'Việc của tôi', params: { mine: true, sort: 'PICKUP' }, empty: 'Bạn chưa nhận việc nào.' },
      { key: 'open', label: 'Chờ lấy', params: { status: 'PICKUP_PENDING', sort: 'PICKUP' }, countOf: ['PICKUP_PENDING'], empty: 'Không có yêu cầu nào đang chờ tài xế lấy.' },
      { key: 'deliver', label: 'Chờ giao', params: { status: 'WASHED', sort: 'DUE' }, countOf: ['WASHED'], empty: 'Không có đơn giặt xong nào đang chờ giao.' },
    ]
  }
  if (role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT') {
    return [
      { key: 'open', label: 'Chưa có tài xế', params: { status: 'PICKUP_PENDING', sort: 'PICKUP' }, countOf: ['PICKUP_PENDING'], empty: 'Mọi yêu cầu đều đã có tài xế.' },
      { key: 'assigned', label: 'Tài xế đang đến lấy', params: { status: 'PICKUP_ASSIGNED', sort: 'PICKUP' }, countOf: ['PICKUP_ASSIGNED'], empty: 'Không có việc lấy đồ nào đang làm.' },
      { key: 'deliver', label: 'Chờ giao', params: { status: 'WASHED', sort: 'DUE' }, countOf: ['WASHED'], empty: 'Không có đơn giặt xong nào đang chờ giao.' },
      { key: 'delivering', label: 'Đang giao', params: { status: 'DELIVERING', sort: 'DUE' }, countOf: ['DELIVERING'], empty: 'Không có đơn nào đang giao.' },
      { key: 'cancelled', label: 'Đã hủy', params: { cancelled: true }, empty: 'Chưa có yêu cầu / đơn nào bị hủy.' },
    ]
  }
  return [
    { key: 'active', label: 'Đang chờ lấy', params: { status: 'PICKUP_PENDING,PICKUP_ASSIGNED', sort: 'PICKUP' }, countOf: ['PICKUP_PENDING', 'PICKUP_ASSIGNED'], empty: 'Bạn chưa có yêu cầu lấy đồ nào đang chờ.' },
    { key: 'cancelled', label: 'Đã hủy', params: { cancelled: true }, empty: 'Chưa có yêu cầu nào bị hủy.' },
  ]
}

/** Lấy & giao: khách gửi yêu cầu, tài xế nhận việc lấy / giao, quản lý xưởng phân công. */
export default function Pickups() {
  const session = getSession()
  const role = session?.role
  const isCustomer = role ? isCustomerRole(role) : false
  const isDriver = role === 'DRIVER'
  const isAdmin = role === 'WORKSHOP_ADMIN'
  const [params, setParams] = useSearchParams()
  const tabs = role ? tabsFor(role) : []

  const [rows, setRows] = useState<Order[]>([])
  const [counts, setCounts] = useState<Partial<Record<OrderStatus, number>>>({})
  const [mineCount, setMineCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const load = useCallback(() => setReloadKey((k) => k + 1), [])

  const tab = tabs.find((t) => t.key === params.get('tab')) ?? tabs[0]
  const tabKey = tab?.key
  const query = JSON.stringify(tab?.params ?? {})

  useEffect(() => {
    if (!tabKey) return
    let ignore = false
    setLoading(true)
    setError(null)
    const list = (p: object, size: number) => axiosClient.get<OrderPage>('/orders', { params: { ...p, size } }).then((r) => r.data)
    Promise.all([list(JSON.parse(query), 100), list({}, 1), isDriver ? list({ mine: true }, 1) : Promise.resolve(null)])
      .then(([page, all, mine]) => {
        if (ignore) return
        setRows(page.content)
        setCounts(all.counts)
        setMineCount(mine?.total ?? 0)
      })
      .catch((e) => !ignore && setError(getErrorMessage(e, 'Không tải được việc lấy & giao')))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [tabKey, query, isDriver, reloadKey])

  const actions = useOrderActions(load)
  const { openCreate, canCreate } = actions

  const setTab = (key: string) => {
    const next = new URLSearchParams(params)
    next.set('tab', key)
    setParams(next, { replace: true })
  }

  // Link /pickups?new=1 (nút "Yêu cầu lấy đồ" ở nơi khác) mở thẳng form, không phải bấm thêm lần nữa
  const wantsNew = params.get('new') === '1'
  useEffect(() => {
    if (!wantsNew) return
    if (canCreate) openCreate()
    const next = new URLSearchParams(params)
    next.delete('new')
    setParams(next, { replace: true })
  }, [wantsNew, canCreate, openCreate, params, setParams])

  if (!role || !tab) return null

  const countFor = (t: JobTab) => (t.key === 'mine' ? mineCount : t.countOf ? t.countOf.reduce((n, s) => n + (counts[s] ?? 0), 0) : null)
  const emptyAction =
    tab.key === 'mine'
      ? { label: 'Xem việc chờ lấy', onClick: () => setTab('open') }
      : canCreate && tab.key !== 'cancelled'
        ? { label: isAdmin ? 'Tạo yêu cầu hộ khách' : 'Yêu cầu lấy đồ', icon: <PlusOutlined />, onClick: openCreate }
        : undefined

  return (
    <>
      <PageHeader
        title={isCustomer ? 'Yêu cầu lấy đồ' : isDriver ? 'Việc lấy & giao' : 'Lấy & giao'}
        description={
          (isCustomer && 'Gửi yêu cầu khi cần xưởng đến lấy đồ bẩn, theo dõi khi nào tài xế đến.') ||
          (isDriver && 'Nhận việc lấy đồ / giao trả, gọi và chỉ đường tới khách, khách ký ngay trên máy khi nhận / giao.') ||
          (isAdmin && 'Phân công tài xế lấy đồ và giao trả; ưu tiên việc chưa có tài xế và quá ngày hẹn.') ||
          undefined
        }
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              Làm mới
            </Button>
            {canCreate && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                {isAdmin ? 'Tạo yêu cầu hộ khách' : 'Yêu cầu lấy đồ'}
              </Button>
            )}
          </>
        }
      />

      <LoadError error={error} onRetry={load} />

      <Tabs
        activeKey={tab.key}
        onChange={setTab}
        items={tabs.map((t) => {
          const n = countFor(t)
          return { key: t.key, label: n === null ? t.label : `${t.label} (${n})` }
        })}
      />

      {loading && rows.length === 0 ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : rows.length === 0 ? (
        <EmptyState description={tab.empty} action={emptyAction} />
      ) : (
        <CardGrid wide>
          {rows.map((o) => (
            <OrderCard
              key={o.id}
              order={o}
              actions={tab.key === 'cancelled' ? [] : actions.actionsFor(o)}
              busy={actions.busyId === o.id}
              onDetail={tab.key === 'cancelled' ? undefined : () => actions.openDetail(o.id)}
              hideCustomer={isCustomer}
              hint={orderHint(o, role)}
              onMenuAction={isAdmin ? actions.runFromMenu : undefined}
            />
          ))}
        </CardGrid>
      )}

      {actions.ui}
    </>
  )
}
