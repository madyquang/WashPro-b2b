import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Badge, Button, Card, Checkbox, Grid, Input, Pagination, Select, Skeleton, Space, Table, Tabs, Tag, Tooltip, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import CategoryTag from '../components/CategoryTag'
import DueTag from '../components/DueTag'
import EmptyState from '../components/EmptyState'
import PageHeader from '../components/PageHeader'
import StatusTag from '../components/StatusTag'
import ToneTag from '../components/ToneTag'
import { statusColor } from '../utils/statusLook'
import CreateOrderModal from './orders/CreateOrderModal'
import { CompactActions, OrderCard } from './orders/OrderCard'
import { fieldViews, orderHint, type OrderView } from './orders/hints'
import { useOrderActions } from './orders/useOrderActions'
import { getSession, isRestaurantRole } from '../utils/auth'
import { formatQuantity, groupByCategory } from '../utils/catalog'
import { formatCurrency, formatDateTime, getErrorMessage } from '../utils/format'
import { CATEGORY_META, CATEGORY_ORDER, STATUS_META, STATUS_ORDER, type ItemCategory, type Order, type OrderPage, type OrderStatus } from '../types'

/** NEWEST: mới nhận trước; DUE: hạn trả gần nhất trước. */
type SortKey = 'NEWEST' | 'DUE'

const PAGE_SIZE = 10

/** Tab của xưởng: Tất cả + từng trạng thái (dùng ?status=, giữ nguyên các link cũ). */
const WORKSHOP_VIEWS: OrderView[] = [
  { key: 'ALL', label: 'Tất cả', statuses: [], empty: 'Chưa có đơn hàng nào.' },
  ...STATUS_ORDER.map((s) => ({ key: s, label: STATUS_META[s].label, statuses: [s], empty: `Không có đơn nào "${STATUS_META[s].label.toLowerCase()}".` })),
]

export default function Orders() {
  const session = getSession()
  const role = session?.role
  const [params, setParams] = useSearchParams()
  const screens = Grid.useBreakpoint()

  const [result, setResult] = useState<OrderPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState<ItemCategory | undefined>()
  const [sort, setSort] = useState<SortKey>('NEWEST')
  const [paging, setPaging] = useState({ key: '', page: 1 })
  /** Tăng lên để tải lại trang hiện tại (sau thao tác, nút Làm mới). */
  const [reloadKey, setReloadKey] = useState(0)
  const load = useCallback(() => setReloadKey((k) => k + 1), [])
  const [creating, setCreating] = useState(false)
  const orderActions = useOrderActions(load)
  const overdueOnly = params.get('overdue') === '1'

  // Nhóm việc: khách / tài xế dùng ?view= (vài nhóm dễ hiểu), xưởng dùng ?status= (từng trạng thái)
  const field = role ? fieldViews(role) : null
  const views = field ?? WORKSHOP_VIEWS
  const statusParam = params.get('status') as OrderStatus | null
  const viewKey = field
    ? (field.find((v) => v.key === params.get('view'))?.key ??
      (statusParam && field.find((v) => v.statuses.includes(statusParam))?.key) ??
      field[0].key)
    : STATUS_ORDER.includes(statusParam as OrderStatus)
      ? (statusParam as OrderStatus)
      : 'ALL'
  const view = views.find((v) => v.key === viewKey) ?? views[0]
  const statuses = view.statuses.join(',')

  // Gõ tìm kiếm: chờ người dùng ngừng gõ một chút rồi mới gọi API
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(search.trim()), 350)
    return () => clearTimeout(timer)
  }, [search])

  // Đổi bộ lọc hoặc thứ tự thì quay về trang đầu (trang được gắn với bộ lọc đang dùng)
  const filterKey = [statuses, overdueOnly, category, keyword, sort].join('|')
  const page = paging.key === filterKey ? paging.page : 1
  const setPage = useCallback((p: number) => setPaging({ key: filterKey, page: p }), [filterKey])

  // Lọc, phân trang chạy ở máy chủ; chỉ tải đúng trang đang xem
  useEffect(() => {
    // Đổi bộ lọc khi yêu cầu trước chưa xong: bỏ kết quả cũ
    let ignore = false
    setLoading(true)
    setError(null)
    axiosClient
      .get<OrderPage>('/orders', {
        params: {
          status: statuses || undefined,
          overdue: overdueOnly || undefined,
          category,
          q: keyword || undefined,
          sort,
          page: page - 1,
          size: PAGE_SIZE,
        },
      })
      .then(({ data }) => {
        if (ignore) return
        // Trang hiện tại trống (vừa hủy đơn cuối của trang): lùi về trang trước
        if (data.content.length === 0 && page > 1) setPage(page - 1)
        else setResult(data)
      })
      .catch((e) => {
        if (!ignore) setError(getErrorMessage(e, 'Không tải được danh sách đơn hàng'))
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [statuses, overdueOnly, category, keyword, sort, page, setPage, reloadKey])

  const visible = result?.content ?? []
  const total = result?.total ?? 0
  const overdueCount = result?.overdueCount ?? 0
  /** Số đơn của mỗi nhóm việc (cộng số đếm theo trạng thái do máy chủ trả về). */
  const countOf = useMemo(() => {
    const byStatus = result?.counts
    return (v: OrderView) => (v.statuses.length ? v.statuses : STATUS_ORDER).reduce((n, s) => n + (byStatus?.[s] ?? 0), 0)
  }, [result])

  if (!role) return null
  const isDriver = role === 'DRIVER'
  const isRestaurant = isRestaurantRole(role)
  const isAdmin = role === 'WORKSHOP_ADMIN'
  const isAccountant = role === 'WORKSHOP_ACCOUNTANT'
  // Tài xế và nhân viên phía khách không cần thấy tiền
  const canSeeMoney = isAdmin || isAccountant || role === 'RESTAURANT_MANAGER'

  const setOverdueOnly = (on: boolean) => {
    const next = new URLSearchParams(params)
    if (on) next.set('overdue', '1')
    else next.delete('overdue')
    setParams(next)
  }

  const changeView = (key: string) => {
    const next = new URLSearchParams(params)
    next.delete('status')
    next.delete('view')
    if (field) next.set('view', key)
    else if (key !== 'ALL') next.set('status', key)
    setParams(next)
  }

  const allColumns: TableColumnsType<Order> = [
    { title: 'Mã', dataIndex: 'id', width: 70, render: (id: number) => `#${id}` },
    { title: 'Khách hàng', dataIndex: 'restaurantName', ellipsis: true },
    { title: 'Tài xế', dataIndex: 'driverName', responsive: ['xxl'] },
    { title: 'Túi', dataIndex: 'totalBags', align: 'right', width: 56 },
    {
      title: 'Đồ giặt',
      key: 'categories',
      responsive: ['md'],
      render: (_: unknown, o) => {
        const groups = groupByCategory(o.items)
        const hidden = groups.slice(2)
        return (
          <Space size={[4, 4]} wrap>
            {groups.slice(0, 2).map((g) => (
              <Tooltip key={g.category} title={g.items.map((i) => `${i.itemName}: ${formatQuantity(i.quantity, i.unit)}`).join(', ')}>
                <span>
                  <CategoryTag category={g.category} />
                </span>
              </Tooltip>
            ))}
            {hidden.length > 0 && (
              <Tooltip title={hidden.map((g) => g.label).join(', ')}>
                <Tag>+{hidden.length}</Tag>
              </Tooltip>
            )}
          </Space>
        )
      },
    },
    { title: 'Trạng thái', dataIndex: 'status', width: 140, render: (s: OrderStatus) => <StatusTag status={s} /> },
    {
      title: 'Hạn trả',
      key: 'due',
      // Sắp xếp ở máy chủ (trên toàn bộ đơn, không chỉ trang đang xem)
      sorter: true,
      sortOrder: sort === 'DUE' ? 'ascend' : null,
      sortDirections: ['ascend'],
      render: (_: unknown, o) => <DueTag order={o} />,
    },
    {
      title: 'Thành tiền',
      key: 'amount',
      align: 'right',
      responsive: ['lg'],
      render: (_: unknown, o) =>
        o.items.length === 0 ? (
          <Typography.Text type="secondary">Chưa có chi tiết</Typography.Text>
        ) : (
          <>
            {formatCurrency(o.totalAmount)} {!o.billable && <ToneTag tone="orange">thiếu giá</ToneTag>}
          </>
        ),
    },
    { title: 'Ngày nhận', dataIndex: 'createdAt', responsive: ['xxl'], render: (v: string) => formatDateTime(v) },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 250,
      render: (_: unknown, o) => (
        <CompactActions
          actions={orderActions.actionsFor(o)}
          busy={orderActions.busyId === o.id}
          onDetail={() => orderActions.openDetail(o.id)}
          onMenuAction={orderActions.runFromMenu}
        />
      ),
    },
  ]
  // Phía khách: mọi đơn đều của chính họ nên bỏ cột khách hàng; chỉ vai trò được xem tiền mới có cột thành tiền
  const columns = allColumns.filter((c) => (c.key !== 'amount' || canSeeMoney) && (!isRestaurant || !('dataIndex' in c && c.dataIndex === 'restaurantName')))

  const filtered = overdueOnly || !!category || !!keyword
  const empty = filtered ? (
    <EmptyState description="Không có đơn nào khớp bộ lọc." />
  ) : (
    <EmptyState
      description={view.empty}
      action={
        isDriver && view.key === 'all'
          ? { label: 'Tạo đơn', icon: <PlusOutlined />, onClick: () => setCreating(true) }
          : field && view.key !== 'all'
            ? { label: 'Xem tất cả đơn', onClick: () => changeView('all') }
            : undefined
      }
    />
  )

  const tabs = views.map((v) => {
    const n = countOf(v)
    const first = v.statuses[0]
    return {
      key: v.key,
      // Nhóm cần làm có huy hiệu nổi bật; các nhóm khác hiện số trong ngoặc
      label:
        v.statuses.length === 1 && !field ? (
          <Badge count={n} size="small" offset={[10, -2]} color={statusColor(first)} showZero={false}>
            {v.label}
          </Badge>
        ) : (
          `${v.label} (${n})`
        ),
    }
  })

  return (
    <>
      <PageHeader
        title={isRestaurant ? 'Đơn giặt' : isDriver ? 'Đơn đã tạo' : 'Đơn hàng'}
        description={
          (isDriver && 'Đơn bạn đã tạo khi lấy đồ: theo dõi khách xác nhận, báo sai lệch khi cần.') ||
          (isRestaurant && 'Xác nhận số lượng khi tài xế lấy đồ, xác nhận khi đã nhận đủ đồ sạch.') ||
          (isAccountant && 'Nhập đơn giá cho đơn đang giặt để xưởng giao trả.') ||
          'Điều phối đơn từ lúc nhận đồ đến khi giao trả.'
        }
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              Làm mới
            </Button>
            {isDriver && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
                Tạo đơn
              </Button>
            )}
          </>
        }
      />

      <div className="filter-bar">
        <Input.Search
          allowClear
          aria-label="Tìm đơn"
          placeholder={isRestaurant ? 'Tìm loại đồ, mã đơn' : 'Tìm khách hàng, tài xế, loại đồ, mã đơn'}
          style={{ width: 300 }}
          onChange={(e) => setSearch(e.target.value)}
        />
        <Select
          allowClear
          aria-label="Lọc theo nhóm đồ"
          placeholder="Lọc theo nhóm đồ"
          style={{ width: 200 }}
          value={category}
          onChange={setCategory}
          options={CATEGORY_ORDER.map((c) => ({ value: c, label: CATEGORY_META[c].label }))}
        />
        <Checkbox checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)}>
          Chỉ đơn trễ hạn{overdueCount > 0 && <Typography.Text type="danger"> ({overdueCount})</Typography.Text>}
        </Checkbox>
      </div>

      <LoadError error={error} onRetry={load} />

      <Tabs activeKey={view.key} items={tabs} onChange={changeView} />

      {screens.lg ? (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<Order>
            rowKey="id"
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={visible}
            scroll={{ x: 980 }}
            pagination={{ current: page, pageSize: PAGE_SIZE, total, onChange: setPage, showSizeChanger: false, showTotal: (t) => `${t} đơn`, style: { paddingInline: 16 } }}
            onChange={(_pagination, _filters, sorter, extra) => {
              if (extra.action === 'sort') setSort(!Array.isArray(sorter) && sorter.order ? 'DUE' : 'NEWEST')
            }}
            locale={{ emptyText: empty }}
          />
        </Card>
      ) : loading && visible.length === 0 ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : visible.length === 0 ? (
        empty
      ) : (
        // Điện thoại / máy tính bảng: thẻ có đủ nút và lời nhắc việc cần làm
        <>
          <div className="data-card-list">
            {visible.map((o) => (
              <OrderCard
                key={o.id}
                order={o}
                actions={orderActions.actionsFor(o)}
                busy={orderActions.busyId === o.id}
                onDetail={() => orderActions.openDetail(o.id)}
                showMoney={canSeeMoney}
                hideCustomer={isRestaurant}
                hint={orderHint(o, role)}
                // Xưởng có nhiều thao tác trên một đơn: thu gọn vào menu "Thêm"
                onMenuAction={field ? undefined : orderActions.runFromMenu}
              />
            ))}
          </div>
          {total > PAGE_SIZE && (
            <Pagination size="small" align="center" className="mt-4" current={page} pageSize={PAGE_SIZE} total={total} onChange={setPage} showSizeChanger={false} />
          )}
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
      {orderActions.ui}
    </>
  )
}
