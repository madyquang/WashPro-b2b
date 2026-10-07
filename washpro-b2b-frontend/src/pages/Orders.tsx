import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Alert, Badge, Button, Card, Checkbox, Dropdown, Grid, Input, List, Modal, Popconfirm, Select, Space, Table, Tabs, Tag, Tooltip, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { MoreOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import DueTag from '../components/DueTag'
import StatusTag from '../components/StatusTag'
import CreateOrderModal from './orders/CreateOrderModal'
import DiscrepancyModal from './orders/DiscrepancyModal'
import ItemsModal from './orders/ItemsModal'
import OrderDrawer from './orders/OrderDrawer'
import { getSession, isRestaurantRole } from '../utils/auth'
import { formatQuantity, groupByCategory } from '../utils/catalog'
import { formatCurrency, formatDateTime, getErrorMessage, toLocalInput } from '../utils/format'
import { CATEGORY_META, CATEGORY_ORDER, STATUS_META, STATUS_ORDER, type ItemCategory, type Order, type OrderStatus } from '../types'

type Filter = 'ALL' | OrderStatus

interface AdminStep {
  status: OrderStatus
  label: string
  confirm: string
  /** Cần đủ đơn giá mới làm được (giao trả / chốt). */
  needsPrice?: boolean
  primary?: boolean
}

/** Các bước Quản lý xưởng có thể đẩy đơn đi. */
const ADMIN_NEXT: Partial<Record<OrderStatus, AdminStep[]>> = {
  DISCREPANCY: [{ status: 'PENDING_CONFIRM', label: 'Mở lại', confirm: 'Mở lại đơn, gửi cho khách xác nhận lại số lượng?', primary: true }],
  WASHING: [{ status: 'DELIVERING', label: 'Bắt đầu giao', confirm: 'Đồ đã giặt xong, bắt đầu giao trả?', needsPrice: true, primary: true }],
  DELIVERING: [
    { status: 'COMPLETED', label: 'Chốt thay khách', confirm: 'Khách chưa bấm xác nhận. Xưởng chốt hoàn thành thay khách?', needsPrice: true },
  ],
  DELIVERY_ISSUE: [
    { status: 'DELIVERING', label: 'Giao bù', confirm: 'Giao bù phần thiếu/hỏng cho khách?', primary: true },
    { status: 'COMPLETED', label: 'Chốt hoàn thành', confirm: 'Đã xử lý xong sự cố (đã điều chỉnh chi tiết/giá nếu cần)?', needsPrice: true },
  ],
}

export default function Orders() {
  const session = getSession()
  const role = session?.role
  const [params, setParams] = useSearchParams()
  const screens = Grid.useBreakpoint()
  const [modal, modalHolder] = Modal.useModal()

  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState<ItemCategory | undefined>()
  const [busyId, setBusyId] = useState<number | null>(null)

  const [creating, setCreating] = useState(false)
  const [discrepancyOrder, setDiscrepancyOrder] = useState<Order | null>(null)
  const [itemsOrder, setItemsOrder] = useState<Order | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [cancelOrder, setCancelOrder] = useState<Order | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [issueOrder, setIssueOrder] = useState<Order | null>(null)
  const [issueNote, setIssueNote] = useState('')
  const [dueOrder, setDueOrder] = useState<Order | null>(null)
  const [dueValue, setDueValue] = useState('')
  const [dueNote, setDueNote] = useState('')
  const overdueOnly = params.get('overdue') === '1'

  const statusParam = params.get('status')
  const filter: Filter = STATUS_ORDER.includes(statusParam as OrderStatus) ? (statusParam as OrderStatus) : 'ALL'

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.get<Order[]>('/orders')
      setOrders(data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được danh sách đơn hàng'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: orders.length }
    STATUS_ORDER.forEach((s) => (c[s] = 0))
    orders.forEach((o) => (c[o.status] += 1))
    return c
  }, [orders])

  const visible = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    return orders.filter(
      (o) =>
        (filter === 'ALL' || o.status === filter) &&
        (!overdueOnly || o.overdue) &&
        (!category || o.items.some((i) => i.category === category)) &&
        (!keyword ||
          o.restaurantName.toLowerCase().includes(keyword) ||
          o.driverName.toLowerCase().includes(keyword) ||
          o.items.some((i) => i.itemName.toLowerCase().includes(keyword)) ||
          String(o.id) === keyword.replace('#', '')),
    )
  }, [orders, filter, search, category, overdueOnly])

  const overdueCount = useMemo(() => orders.filter((o) => o.overdue).length, [orders])

  if (!role) return null
  const isDriver = role === 'DRIVER'
  const isRestaurant = isRestaurantRole(role)
  const isAdmin = role === 'WORKSHOP_ADMIN'
  const isAccountant = role === 'WORKSHOP_ACCOUNTANT'
  // Tài xế và nhân viên phía khách không cần thấy tiền
  const canSeeMoney = isAdmin || isAccountant || role === 'RESTAURANT_MANAGER'

  const run = async (order: Order, request: () => Promise<unknown>, success: string) => {
    setBusyId(order.id)
    try {
      await request()
      message.success(success)
      await load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  const confirmCancel = async () => {
    if (!cancelOrder) return
    const target = cancelOrder
    setCancelOrder(null)
    await run(
      target,
      () => axiosClient.delete(`/orders/${target.id}`, { params: { reason: cancelReason.trim() || undefined } }),
      `Đã hủy đơn #${target.id}`,
    )
    setCancelReason('')
  }

  const confirmIssue = async () => {
    if (!issueOrder || !issueNote.trim()) return
    const target = issueOrder
    setIssueOrder(null)
    await run(target, () => axiosClient.put(`/orders/${target.id}/receipt-issue`, { note: issueNote.trim() }), 'Đã báo sự cố, xưởng sẽ liên hệ xử lý')
    setIssueNote('')
  }

  const openDue = (o: Order) => {
    setDueOrder(o)
    setDueValue(toLocalInput(o.dueAt))
    setDueNote('')
  }

  const confirmDue = async () => {
    if (!dueOrder || !dueValue) return
    const target = dueOrder
    setDueOrder(null)
    // datetime-local không có múi giờ, gửi nguyên dạng yyyy-MM-ddTHH:mm:00 (giờ địa phương như backend)
    await run(target, () => axiosClient.put(`/orders/${target.id}/due`, { dueAt: `${dueValue}:00`, note: dueNote.trim() || null }), `Đã đổi hạn trả đơn #${target.id}`)
  }

  const setOverdueOnly = (on: boolean) => {
    const next = new URLSearchParams(params)
    if (on) next.set('overdue', '1')
    else next.delete('overdue')
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
          <Space size={[0, 4]} wrap>
            {groups.slice(0, 2).map((g) => (
              <Tooltip key={g.category} title={g.items.map((i) => `${i.itemName}: ${formatQuantity(i.quantity, i.unit)}`).join(', ')}>
                <Tag color={CATEGORY_META[g.category].color}>{g.label}</Tag>
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
    { title: 'Trạng thái', dataIndex: 'status', width: 130, render: (s: OrderStatus) => <StatusTag status={s} /> },
    {
      title: 'Hạn trả',
      key: 'due',
      sorter: (a, b) => (a.dueAt ?? '').localeCompare(b.dueAt ?? ''),
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
            {formatCurrency(o.totalAmount)}
            {!o.billable && <Tag color="orange" style={{ marginInlineStart: 4 }}>thiếu giá</Tag>}
          </>
        ),
    },
    { title: 'Ngày nhận', dataIndex: 'createdAt', responsive: ['xxl'], render: (v: string) => formatDateTime(v) },
    { title: 'Thao tác', key: 'actions', fixed: 'right', width: 236, render: (_: unknown, o) => renderActions(o) },
  ]
  const columns = allColumns.filter((c) => c.key !== 'amount' || canSeeMoney)

  /** Một thao tác trên đơn: có confirm thì hỏi lại trước khi làm. */
  interface OrderAction {
    key: string
    label: string
    primary?: boolean
    danger?: boolean
    disabledReason?: string
    confirm?: { title: string; description?: ReactNode; okText?: string }
    onClick: () => void
  }

  /** Các thao tác theo vai trò và trạng thái đơn; thao tác chính (primary) xếp trước. */
  function orderActions(o: Order): OrderAction[] {
    const waiting = o.status === 'PENDING_CONFIRM' || o.status === 'DISCREPANCY'
    const actions: OrderAction[] = []

    if (isRestaurant && o.status === 'DELIVERING') {
      actions.push(
        {
          key: 'receipt',
          label: 'Đã nhận đủ',
          primary: true,
          confirm: { title: 'Đã nhận đủ đồ sạch?', description: 'Đơn sẽ hoàn thành và được tính vào công nợ.', okText: 'Đã nhận đủ' },
          onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/confirm-receipt`), `Đã xác nhận nhận đồ đơn #${o.id}`),
        },
        { key: 'issue', label: 'Báo thiếu/hỏng', danger: true, onClick: () => setIssueOrder(o) },
      )
    }
    if (isRestaurant && waiting) {
      actions.push({
        key: 'approve',
        label: 'Xác nhận',
        primary: true,
        confirm: {
          title: 'Xác nhận đơn?',
          description: (
            <div style={{ maxWidth: 320 }}>
              Bạn xác nhận số lượng {o.totalBags} túi gồm: {o.items.map((i) => `${i.itemName} ${formatQuantity(i.quantity, i.unit)}`).join(', ')}. Đơn sẽ
              chuyển xưởng giặt.
            </div>
          ),
          okText: 'Xác nhận',
        },
        onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/approve`), `Đã xác nhận đơn #${o.id}`),
      })
    }
    if ((isRestaurant || isDriver) && waiting) {
      actions.push({ key: 'discrepancy', label: 'Báo sai lệch', danger: true, onClick: () => setDiscrepancyOrder(o) })
    }
    if (isAdmin) {
      for (const step of ADMIN_NEXT[o.status] ?? []) {
        actions.push({
          key: `step-${step.status}`,
          label: step.label,
          primary: step.primary,
          disabledReason: step.needsPrice && !o.billable ? 'Cần nhập đơn giá cho mọi dòng (Chi tiết & giá) trước' : undefined,
          confirm: { title: step.confirm },
          onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/status`, { status: step.status }), `Đơn #${o.id}: ${STATUS_META[step.status].label}`),
        })
      }
    }
    if ((isAdmin || isAccountant) && o.status !== 'COMPLETED') {
      actions.push({ key: 'items', label: 'Chi tiết & giá', primary: isAccountant && !o.billable, onClick: () => setItemsOrder(o) })
    }
    if (isAdmin && o.status !== 'COMPLETED') {
      actions.push(
        { key: 'due', label: 'Đổi hạn trả', onClick: () => openDue(o) },
        { key: 'cancel', label: 'Hủy đơn', danger: true, onClick: () => setCancelOrder(o) },
      )
    }
    return [...actions.filter((a) => a.primary), ...actions.filter((a) => !a.primary)]
  }

  /** Một nút thao tác (kèm hỏi lại nếu cần). */
  function actionButton(o: Order, a: OrderAction, block = false) {
    const button = (
      <Button
        key={a.key}
        size={block ? 'middle' : 'small'}
        type={a.primary ? 'primary' : 'default'}
        danger={a.danger}
        loading={a.primary && busyId === o.id}
        disabled={!!a.disabledReason}
        onClick={a.confirm ? undefined : a.onClick}
      >
        {a.label}
      </Button>
    )
    if (a.disabledReason) {
      return (
        <Tooltip key={a.key} title={a.disabledReason}>
          {button}
        </Tooltip>
      )
    }
    return a.confirm ? (
      <Popconfirm key={a.key} title={a.confirm.title} description={a.confirm.description} okText={a.confirm.okText ?? 'Đồng ý'} cancelText="Đóng" onConfirm={a.onClick}>
        {button}
      </Popconfirm>
    ) : (
      button
    )
  }

  /** Bảng (máy tính): Chi tiết + một thao tác chính, phần còn lại trong menu "⋯" để cột gọn. */
  function renderActions(o: Order) {
    const actions = orderActions(o)
    const main = actions.find((a) => a.primary)
    const rest = actions.filter((a) => a !== main)
    return (
      <Space size={4}>
        <Button size="small" onClick={() => setDetailId(o.id)}>
          Chi tiết
        </Button>
        {main && actionButton(o, main)}
        {rest.length > 0 && (
          <Dropdown
            trigger={['click']}
            menu={{
              items: rest.map((a) => ({
                key: a.key,
                label: a.disabledReason ? <Tooltip title={a.disabledReason}>{a.label}</Tooltip> : a.label,
                danger: a.danger,
                disabled: !!a.disabledReason,
                onClick: () =>
                  a.confirm
                    ? modal.confirm({ title: a.confirm.title, content: a.confirm.description, okText: a.confirm.okText ?? 'Đồng ý', cancelText: 'Đóng', onOk: a.onClick })
                    : a.onClick(),
              })),
            }}
          >
            <Button size="small" icon={<MoreOutlined />} aria-label="Thao tác khác" />
          </Dropdown>
        )}
      </Space>
    )
  }

  /** Thẻ (điện thoại): hiện đủ nút, dễ bấm. */
  function renderCardActions(o: Order) {
    return (
      <>
        <Button size="middle" onClick={() => setDetailId(o.id)}>
          Chi tiết
        </Button>
        {orderActions(o).map((a) => actionButton(o, a, true))}
      </>
    )
  }

  const tabs = [
    { key: 'ALL', label: `Tất cả (${counts.ALL})` },
    ...STATUS_ORDER.map((s) => ({
      key: s,
      label: (
        <Badge count={counts[s]} size="small" offset={[10, -2]} color={STATUS_META[s].color} showZero={false}>
          {STATUS_META[s].label}
        </Badge>
      ),
    })),
  ]

  return (
    <>
      <div className="page-toolbar">
        <Typography.Title level={3} style={{ margin: 0 }}>
          Đơn hàng
        </Typography.Title>
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="Tìm khách hàng, tài xế, loại đồ, mã đơn"
            style={{ width: 280, maxWidth: '100%' }}
            onChange={(e) => setSearch(e.target.value)}
          />
          <Select
            allowClear
            placeholder="Lọc theo nhóm đồ"
            style={{ width: 190 }}
            value={category}
            onChange={setCategory}
            options={CATEGORY_ORDER.map((c) => ({ value: c, label: CATEGORY_META[c].label }))}
          />
          <Checkbox checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)}>
            Chỉ đơn trễ hạn{overdueCount > 0 && <Typography.Text type="danger"> ({overdueCount})</Typography.Text>}
          </Checkbox>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading} aria-label="Làm mới">
            {screens.md && 'Làm mới'}
          </Button>
          {isDriver && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
              Tạo đơn
            </Button>
          )}
        </Space>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          title={error}
          action={<Button size="small" onClick={load}>Thử lại</Button>}
        />
      )}

      <Tabs
        activeKey={filter}
        items={tabs}
        onChange={(key) => {
          const next = new URLSearchParams(params)
          if (key === 'ALL') next.delete('status')
          else next.set('status', key)
          setParams(next)
        }}
      />

      {screens.lg ? (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<Order>
            rowKey="id"
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={visible}
            scroll={{ x: 980 }}
            pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (t) => `${t} đơn`, style: { paddingInline: 16 } }}
            locale={{ emptyText: 'Không có đơn hàng nào' }}
          />
        </Card>
      ) : (
        <List<Order>
          loading={loading}
          dataSource={visible}
          locale={{ emptyText: 'Không có đơn hàng nào' }}
          pagination={visible.length > 10 ? { pageSize: 10, size: 'small', align: 'center' } : false}
          renderItem={(o) => (
            <Card key={o.id} className="order-card" style={{ marginBottom: 12, borderColor: o.overdue ? '#ffa39e' : undefined }}>
              <div className="order-card-head">
                <div style={{ minWidth: 0 }}>
                  <div className="order-card-title">
                    #{o.id} · {o.restaurantName}
                  </div>
                  <div className="order-card-meta">
                    {o.totalBags} túi · {o.driverName} · {formatDateTime(o.createdAt)}
                  </div>
                </div>
                <StatusTag status={o.status} />
              </div>
              <Space size={[4, 4]} wrap style={{ marginTop: 10 }}>
                {groupByCategory(o.items).map((g) => (
                  <Tag key={g.category} color={CATEGORY_META[g.category].color}>
                    {g.label}
                  </Tag>
                ))}
                {o.status !== 'COMPLETED' && o.dueAt && <DueTag order={o} />}
              </Space>
              {canSeeMoney && o.items.length > 0 && (
                <div style={{ marginTop: 8 }}>
                  <Typography.Text strong>{formatCurrency(o.totalAmount)}</Typography.Text>
                  {!o.billable && (
                    <Tag color="orange" style={{ marginInlineStart: 6 }}>
                      thiếu giá
                    </Tag>
                  )}
                </div>
              )}
              <div className="order-card-actions">{renderCardActions(o)}</div>
            </Card>
          )}
        />
      )}

      <CreateOrderModal
        open={creating}
        onClose={() => setCreating(false)}
        onDone={() => {
          setCreating(false)
          load()
        }}
      />
      <DiscrepancyModal
        order={discrepancyOrder}
        onClose={() => setDiscrepancyOrder(null)}
        onDone={() => {
          setDiscrepancyOrder(null)
          load()
        }}
      />
      <ItemsModal
        order={itemsOrder}
        onClose={() => setItemsOrder(null)}
        onDone={() => {
          setItemsOrder(null)
          load()
        }}
      />
      <OrderDrawer orderId={detailId} onClose={() => setDetailId(null)} />
      {modalHolder}

      <Modal
        title={cancelOrder ? `Hủy đơn #${cancelOrder.id}?` : ''}
        open={cancelOrder !== null}
        onCancel={() => setCancelOrder(null)}
        onOk={confirmCancel}
        okText="Hủy đơn"
        cancelText="Đóng"
        okButtonProps={{ danger: true }}
        destroyOnHidden
      >
        <Typography.Paragraph>
          Đơn sẽ bị ẩn khỏi danh sách nhưng vẫn được lưu để đối soát. Không thể hoàn tác.
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="Lý do hủy (không bắt buộc)"
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
        />
      </Modal>

      <Modal
        title={issueOrder ? `Báo thiếu/hỏng · đơn #${issueOrder.id}` : ''}
        open={issueOrder !== null}
        onCancel={() => setIssueOrder(null)}
        onOk={confirmIssue}
        okText="Gửi báo cáo"
        cancelText="Đóng"
        okButtonProps={{ danger: true, disabled: !issueNote.trim() }}
        destroyOnHidden
      >
        <Typography.Paragraph>
          Mô tả cụ thể món bị thiếu hoặc hỏng. Xưởng sẽ giao bù hoặc điều chỉnh số tiền trước khi chốt đơn.
        </Typography.Paragraph>
        <Input.TextArea
          rows={4}
          maxLength={500}
          showCount
          placeholder="Ví dụ: thiếu 2 vỏ gối, 1 áo đầu bếp còn vết dầu..."
          value={issueNote}
          onChange={(e) => setIssueNote(e.target.value)}
        />
      </Modal>

      <Modal
        title={dueOrder ? `Đổi hạn trả · đơn #${dueOrder.id}` : ''}
        open={dueOrder !== null}
        onCancel={() => setDueOrder(null)}
        onOk={confirmDue}
        okText="Lưu"
        cancelText="Đóng"
        okButtonProps={{ disabled: !dueValue }}
        destroyOnHidden
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input type="datetime-local" value={dueValue} onChange={(e) => setDueValue(e.target.value)} />
          <Input.TextArea rows={2} maxLength={500} placeholder="Lý do (VD: khách đồng ý lùi hạn)" value={dueNote} onChange={(e) => setDueNote(e.target.value)} />
        </Space>
      </Modal>
    </>
  )
}
