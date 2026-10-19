import type { ReactNode } from 'react'
import { Button, Card, Dropdown, Popconfirm, Space, Tooltip, Typography } from 'antd'
import { CarOutlined, EnvironmentOutlined, MoreOutlined, PhoneOutlined, WarningOutlined } from '@ant-design/icons'
import CategoryTag from '../../components/CategoryTag'
import DueTag from '../../components/DueTag'
import StatusTag from '../../components/StatusTag'
import ToneTag from '../../components/ToneTag'
import { getSession, isCustomerRole } from '../../utils/auth'
import { formatQuantity, groupByCategory } from '../../utils/catalog'
import { telHref } from '../../utils/company'
import { formatCurrency, formatDate, formatDateTime, todayIso } from '../../utils/format'
import { isPickupStage, TIME_SLOT_LABELS, type Order } from '../../types'
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

/** Nút gọi khách của đơn: chỉ phía xưởng và tài xế (khách không cần gọi chính mình). */
export function CallCustomer({ order, compact }: { order: Order; compact?: boolean }) {
  const role = getSession()?.role
  if (!order.customerPhone || !role || isCustomerRole(role)) return null
  const label = `Gọi ${order.contactName ?? order.customerName}: ${order.customerPhone}`
  return (
    <Tooltip title={label}>
      <Button size={compact ? 'small' : 'middle'} icon={<PhoneOutlined />} href={telHref(order.customerPhone)} aria-label={label}>
        {!compact && 'Gọi'}
      </Button>
    </Tooltip>
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
  /** Không truyền: ẩn nút Chi tiết (VD yêu cầu đã hủy). */
  onDetail?: () => void
  /** Hiện số tiền (chỉ vai trò được xem tiền). */
  showMoney?: boolean
  /** Ẩn tên khách hàng (tài khoản phía khách: luôn là đơn của chính họ). */
  hideCustomer?: boolean
  /** Dòng nhắc việc phía trên các nút (VD "Tài xế đã đếm, kiểm lại rồi xác nhận"). */
  hint?: ReactNode
  /** Thu gọn nút: Chi tiết + thao tác chính + menu "Thêm" (xưởng xem trên điện thoại, đơn có nhiều thao tác). */
  onMenuAction?: (a: OrderAction) => void
}

/** "Hôm nay" / "Ngày mai" / ngày cụ thể. */
export function whenLabel(date: string) {
  const today = todayIso()
  if (date === today) return 'Hôm nay'
  const t = new Date(`${today}T00:00:00`)
  t.setDate(t.getDate() + 1)
  const tomorrow = `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
  return date === tomorrow ? 'Ngày mai' : formatDate(date)
}

const mapsUrl = (address: string) => `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`

/** Dòng tóm tắt dưới tiêu đề: yêu cầu chưa lấy thì ngày hẹn; đã lấy thì số túi, tài xế, lúc nhận. */
function metaLine(o: Order, hideCustomer?: boolean) {
  // Tài xế không cần thấy tên chính mình trên việc của mình
  const me = getSession()?.username
  if (o.driverUsername === me || o.deliveryDriverUsername === me) {
    return isPickupStage(o) && o.preferredDate
      ? `${whenLabel(o.preferredDate)} · ${o.timeSlot ? TIME_SLOT_LABELS[o.timeSlot] : ''}`
      : [o.totalBags != null ? `${o.totalBags} túi` : null, o.receivedAt ? `nhận ${formatDateTime(o.receivedAt)}` : null].filter(Boolean).join(' · ')
  }
  if (isPickupStage(o)) {
    const when = o.preferredDate ? `${whenLabel(o.preferredDate)} · ${o.timeSlot ? TIME_SLOT_LABELS[o.timeSlot] : ''}` : ''
    return o.driverName ? `${when} · tài xế ${o.driverName}` : when
  }
  const driver = o.deliveryDriverName ?? o.driverName
  return [
    o.totalBags != null ? `${o.totalBags} túi` : null,
    driver ? (hideCustomer ? `tài xế ${driver}` : driver) : null,
    o.receivedAt ? `nhận ${formatDateTime(o.receivedAt)}` : null,
  ]
    .filter(Boolean)
    .join(' · ')
}

/** Thẻ đơn: thông tin cần để quyết định + đủ nút thao tác, dùng trên điện thoại và trang chủ các vai trò. */
export function OrderCard({ order: o, actions, busy, onDetail, showMoney, hideCustomer, hint, onMenuAction }: CardProps) {
  const main = onMenuAction ? actions.find((a) => a.primary) : undefined
  const rest = onMenuAction ? actions.filter((a) => a !== main) : []
  const pickup = isPickupStage(o)
  const role = getSession()?.role
  // Tài xế ngoài đường: chỉ đường tới khách khi đi lấy hoặc đi giao
  const showMap = role === 'DRIVER' && !!o.customerAddress && (pickup || o.status === 'WASHED' || o.status === 'DELIVERING')
  const flagged = o.overdue || o.pickupLate
  return (
    <Card className={flagged ? 'order-card is-flagged' : 'order-card'}>
      <div className="order-card-head">
        <div style={{ minWidth: 0 }}>
          <div className="order-card-title">{hideCustomer ? `${pickup ? 'Yêu cầu' : 'Đơn'} #${o.id}` : `#${o.id} · ${o.customerName}`}</div>
          <div className="order-card-meta">{metaLine(o, hideCustomer)}</div>
        </div>
        <StatusTag status={o.status} />
      </div>
      {pickup ? (
        <div className="pickup-body mt-2">
          {o.pickupLate && (
            <Typography.Text type="danger" strong>
              <WarningOutlined aria-hidden /> Quá ngày hẹn lấy
            </Typography.Text>
          )}
          {!hideCustomer && o.customerAddress && (
            <span>
              <EnvironmentOutlined aria-hidden /> {o.customerAddress}
            </span>
          )}
          {o.estimatedBags != null && (
            <span>
              <CarOutlined aria-hidden /> Khoảng {o.estimatedBags} túi
            </span>
          )}
          {o.declaredItems.length > 0 && (
            <span className="order-card-meta">Khách khai: {o.declaredItems.map((d) => `${d.itemName} ${formatQuantity(d.quantity, d.unit)}`).join(', ')}</span>
          )}
          {o.pickupNote && <div className="pickup-note pre-wrap">“{o.pickupNote}”</div>}
        </div>
      ) : (
        <Space size={[4, 4]} wrap className="mt-2">
          {groupByCategory(o.items).map((g) => (
            <CategoryTag key={g.category} category={g.category} />
          ))}
          {o.status !== 'COMPLETED' && o.dueAt && <DueTag order={o} />}
        </Space>
      )}
      {showMap && !pickup && (
        <div className="order-card-meta mt-2">
          <EnvironmentOutlined aria-hidden /> {o.customerAddress}
        </div>
      )}
      {o.cancelledBy && (
        <Typography.Text type="secondary" className="mt-2" style={{ display: 'block' }}>
          Hủy bởi {o.cancelledBy}
          {o.cancelReason && `: ${o.cancelReason}`}
        </Typography.Text>
      )}
      {showMoney && o.items.length > 0 && (
        <div className="mt-2">
          <Typography.Text strong>{formatCurrency(o.totalAmount)}</Typography.Text> {!o.billable && <ToneTag tone="orange">thiếu giá</ToneTag>}
        </div>
      )}
      {hint && <div className="order-card-hint">{hint}</div>}
      <div className="order-card-actions">
        {onDetail && (
          <Button size="middle" onClick={onDetail}>
            Chi tiết
          </Button>
        )}
        {!o.cancelledBy && <CallCustomer order={o} />}
        {showMap && o.customerAddress && (
          <Button size="middle" icon={<EnvironmentOutlined />} href={mapsUrl(o.customerAddress)} target="_blank" rel="noreferrer">
            Chỉ đường
          </Button>
        )}
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
    <div className={o.overdue || o.pickupLate ? 'order-row is-flagged' : 'order-row'}>
      <div className="order-row-main">
        <div className="order-card-title">
          #{o.id} · {o.customerName}
        </div>
        <div className="order-card-meta">
          {isPickupStage(o) ? metaLine(o) : `${o.totalBags} túi · ${groupByCategory(o.items).map((g) => g.label).join(', ') || 'chưa có chi tiết'}`}
          {hint && <> · {hint}</>}
        </div>
      </div>
      <Space size={4} wrap className="order-row-tags">
        {o.status !== 'COMPLETED' && o.dueAt && <DueTag order={o} />}
        <StatusTag status={o.status} />
      </Space>
      <Space size={4}>
        <CallCustomer order={o} compact />
        <CompactActions actions={actions} busy={busy} onDetail={onDetail} onMenuAction={onMenuAction} />
      </Space>
    </div>
  )
}
