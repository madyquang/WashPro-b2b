import type { ReactNode } from 'react'
import { Button, Card, Dropdown, Popconfirm, Space, Tooltip, Typography } from 'antd'
import { MoreOutlined } from '@ant-design/icons'
import CategoryTag from '../../components/CategoryTag'
import DueTag from '../../components/DueTag'
import StatusTag from '../../components/StatusTag'
import ToneTag from '../../components/ToneTag'
import { groupByCategory } from '../../utils/catalog'
import { formatCurrency, formatDateTime } from '../../utils/format'
import type { Order } from '../../types'
import type { OrderAction } from './useOrderActions'

/** Một nút thao tác (kèm hỏi lại nếu cần). */
export function ActionButton({ action: a, busy, block }: { action: OrderAction; busy?: boolean; block?: boolean }) {
  const button = (
    <Button
      size={block ? 'middle' : 'small'}
      type={a.primary ? 'primary' : 'default'}
      danger={a.danger}
      loading={a.primary && busy}
      disabled={!!a.disabledReason}
      onClick={a.confirm ? undefined : a.onClick}
    >
      {a.label}
    </Button>
  )
  if (a.disabledReason) return <Tooltip title={a.disabledReason}>{button}</Tooltip>
  return a.confirm ? (
    <Popconfirm title={a.confirm.title} description={a.confirm.description} okText={a.confirm.okText ?? 'Đồng ý'} cancelText="Đóng" onConfirm={a.onClick}>
      {button}
    </Popconfirm>
  ) : (
    button
  )
}

/** Bảng (máy tính): Chi tiết + một thao tác chính, phần còn lại trong menu "⋯" để cột gọn. */
export function CompactActions({
  actions,
  busy,
  onDetail,
  onMenuAction,
}: {
  actions: OrderAction[]
  busy?: boolean
  onDetail: () => void
  onMenuAction: (a: OrderAction) => void
}) {
  const main = actions.find((a) => a.primary)
  const rest = actions.filter((a) => a !== main)
  return (
    <Space size={4}>
      <Button size="small" onClick={onDetail}>
        Chi tiết
      </Button>
      {main && <ActionButton action={main} busy={busy} />}
      {rest.length > 0 && (
        <Dropdown
          trigger={['click']}
          menu={{
            items: rest.map((a) => ({
              key: a.key,
              label: a.disabledReason ? <Tooltip title={a.disabledReason}>{a.label}</Tooltip> : a.label,
              danger: a.danger,
              disabled: !!a.disabledReason,
              onClick: () => onMenuAction(a),
            })),
          }}
        >
          <Button size="small" icon={<MoreOutlined />} aria-label="Thao tác khác" />
        </Dropdown>
      )}
    </Space>
  )
}

interface CardProps {
  order: Order
  actions: OrderAction[]
  busy?: boolean
  onDetail: () => void
  /** Hiện số tiền (chỉ vai trò được xem tiền). */
  showMoney?: boolean
  /** Ẩn tên khách hàng (tài khoản phía khách: luôn là đơn của chính họ). */
  hideCustomer?: boolean
  /** Dòng nhắc việc phía trên các nút (VD "Tài xế đã đếm, kiểm lại rồi xác nhận"). */
  hint?: ReactNode
  /** Thu gọn nút: Chi tiết + thao tác chính + menu "Thêm" (xưởng xem trên điện thoại, đơn có nhiều thao tác). */
  onMenuAction?: (a: OrderAction) => void
}

/** Thẻ đơn: thông tin cần để quyết định + đủ nút thao tác, dùng trên điện thoại và trang chủ các vai trò. */
export function OrderCard({ order: o, actions, busy, onDetail, showMoney, hideCustomer, hint, onMenuAction }: CardProps) {
  const main = onMenuAction ? actions.find((a) => a.primary) : undefined
  const rest = onMenuAction ? actions.filter((a) => a !== main) : []
  return (
    <Card className={o.overdue ? 'order-card is-flagged' : 'order-card'}>
      <div className="order-card-head">
        <div style={{ minWidth: 0 }}>
          <div className="order-card-title">{hideCustomer ? `Đơn #${o.id}` : `#${o.id} · ${o.customerName}`}</div>
          <div className="order-card-meta">
            {o.totalBags} túi · {hideCustomer ? `tài xế ${o.driverName}` : o.driverName} · nhận {formatDateTime(o.createdAt)}
          </div>
        </div>
        <StatusTag status={o.status} />
      </div>
      <Space size={[4, 4]} wrap className="mt-2">
        {groupByCategory(o.items).map((g) => (
          <CategoryTag key={g.category} category={g.category} />
        ))}
        {o.status !== 'COMPLETED' && o.dueAt && <DueTag order={o} />}
      </Space>
      {showMoney && o.items.length > 0 && (
        <div className="mt-2">
          <Typography.Text strong>{formatCurrency(o.totalAmount)}</Typography.Text> {!o.billable && <ToneTag tone="orange">thiếu giá</ToneTag>}
        </div>
      )}
      {hint && <div className="order-card-hint">{hint}</div>}
      <div className="order-card-actions">
        <Button size="middle" onClick={onDetail}>
          Chi tiết
        </Button>
        {onMenuAction ? (
          <>
            {main && <ActionButton action={main} busy={busy} block />}
            {rest.length > 0 && (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: rest.map((a) => ({ key: a.key, label: a.label, danger: a.danger, disabled: !!a.disabledReason, onClick: () => onMenuAction(a) })),
                }}
              >
                <Button size="middle" icon={<MoreOutlined />}>
                  Thêm
                </Button>
              </Dropdown>
            )}
          </>
        ) : (
          actions.map((a) => <ActionButton key={a.key} action={a} busy={busy} block />)
        )}
      </div>
    </Card>
  )
}

/** Dòng đơn gọn (máy tính): mã, khách, nhóm đồ, hạn trả, trạng thái và thao tác trên cùng một hàng. */
export function OrderRow({
  order: o,
  actions,
  busy,
  onDetail,
  onMenuAction,
  hint,
}: {
  order: Order
  actions: OrderAction[]
  busy?: boolean
  onDetail: () => void
  onMenuAction: (a: OrderAction) => void
  hint?: ReactNode
}) {
  return (
    <div className={o.overdue ? 'order-row is-flagged' : 'order-row'}>
      <div className="order-row-main">
        <div className="order-card-title">
          #{o.id} · {o.customerName}
        </div>
        <div className="order-card-meta">
          {o.totalBags} túi · {groupByCategory(o.items).map((g) => g.label).join(', ') || 'chưa có chi tiết'}
          {hint && <> · {hint}</>}
        </div>
      </div>
      <Space size={4} wrap className="order-row-tags">
        {o.status !== 'COMPLETED' && o.dueAt && <DueTag order={o} />}
        <StatusTag status={o.status} />
      </Space>
      <CompactActions actions={actions} busy={busy} onDetail={onDetail} onMenuAction={onMenuAction} />
    </div>
  )
}
