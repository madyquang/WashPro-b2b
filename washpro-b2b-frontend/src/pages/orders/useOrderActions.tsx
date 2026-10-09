import { useState, type ReactNode } from 'react'
import { Input, Modal, Space, Typography, message } from 'antd'
import axiosClient from '../../api/axiosClient'
import DiscrepancyModal from './DiscrepancyModal'
import ItemsModal from './ItemsModal'
import OrderDrawer from './OrderDrawer'
import { getSession, isRestaurantRole } from '../../utils/auth'
import { formatQuantity } from '../../utils/catalog'
import { getErrorMessage, toLocalInput } from '../../utils/format'
import { STATUS_META, type Order, type OrderStatus } from '../../types'

/** Một thao tác trên đơn: có confirm thì hỏi lại trước khi làm. */
export interface OrderAction {
  key: string
  label: string
  primary?: boolean
  danger?: boolean
  disabledReason?: string
  confirm?: { title: string; description?: ReactNode; okText?: string }
  onClick: () => void
}

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

/**
 * Toàn bộ thao tác trên đơn theo vai trò + các hộp thoại đi kèm (báo sai lệch, chi tiết & giá, hủy, báo thiếu/hỏng,
 * đổi hạn, xem chi tiết). Dùng chung cho trang Đơn hàng và trang chủ từng vai trò để thao tác được ngay tại chỗ.
 * Gọi onChanged sau mỗi thao tác thành công để nơi dùng tải lại dữ liệu.
 */
export function useOrderActions(onChanged: () => void) {
  const role = getSession()?.role
  const isDriver = role === 'DRIVER'
  const isRestaurant = role ? isRestaurantRole(role) : false
  const isAdmin = role === 'WORKSHOP_ADMIN'
  const isAccountant = role === 'WORKSHOP_ACCOUNTANT'

  const [modal, modalHolder] = Modal.useModal()
  const [busyId, setBusyId] = useState<number | null>(null)
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

  const run = async (order: Order, request: () => Promise<unknown>, success: string) => {
    setBusyId(order.id)
    try {
      await request()
      message.success(success)
      onChanged()
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
    await run(target, () => axiosClient.delete(`/orders/${target.id}`, { params: { reason: cancelReason.trim() || undefined } }), `Đã hủy đơn #${target.id}`)
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

  /** Các thao tác theo vai trò và trạng thái đơn; thao tác chính (primary) xếp trước. */
  const actionsFor = (o: Order): OrderAction[] => {
    const waiting = o.status === 'PENDING_CONFIRM' || o.status === 'DISCREPANCY'
    const actions: OrderAction[] = []

    if (isRestaurant && o.status === 'DELIVERING') {
      actions.push(
        {
          key: 'receipt',
          label: 'Đã nhận đủ đồ',
          primary: true,
          confirm: { title: 'Đã nhận đủ đồ sạch?', description: 'Đơn sẽ hoàn thành và được tính vào công nợ.', okText: 'Đã nhận đủ' },
          onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/confirm-receipt`), `Đã xác nhận nhận đồ đơn #${o.id}`),
        },
        { key: 'issue', label: 'Báo thiếu / hỏng', danger: true, onClick: () => setIssueOrder(o) },
      )
    }
    if (isRestaurant && waiting) {
      actions.push({
        key: 'approve',
        label: 'Xác nhận số lượng',
        primary: true,
        confirm: {
          title: 'Số lượng đã đúng?',
          description: (
            <div style={{ maxWidth: 320 }}>
              {o.totalBags} túi gồm: {o.items.map((i) => `${i.itemName} ${formatQuantity(i.quantity, i.unit)}`).join(', ')}. Đơn sẽ chuyển xưởng giặt.
            </div>
          ),
          okText: 'Đúng, xác nhận',
        },
        onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/approve`), `Đã xác nhận đơn #${o.id}`),
      })
    }
    if ((isRestaurant || isDriver) && waiting) {
      actions.push({ key: 'discrepancy', label: isRestaurant ? 'Số lượng không đúng' : 'Báo sai lệch', danger: true, onClick: () => setDiscrepancyOrder(o) })
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
      actions.push({ key: 'items', label: isAccountant && !o.billable ? 'Nhập đơn giá' : 'Chi tiết & giá', primary: isAccountant && !o.billable, onClick: () => setItemsOrder(o) })
    }
    if (isAdmin && o.status !== 'COMPLETED') {
      actions.push(
        { key: 'due', label: 'Đổi hạn trả', onClick: () => openDue(o) },
        { key: 'cancel', label: 'Hủy đơn', danger: true, onClick: () => setCancelOrder(o) },
      )
    }
    return [...actions.filter((a) => a.primary), ...actions.filter((a) => !a.primary)]
  }

  /** Thao tác trong menu "⋯": hỏi lại bằng hộp thoại (Popconfirm không gắn được vào mục menu). */
  const runFromMenu = (a: OrderAction) =>
    a.confirm
      ? modal.confirm({ title: a.confirm.title, content: a.confirm.description, okText: a.confirm.okText ?? 'Đồng ý', cancelText: 'Đóng', onOk: a.onClick })
      : a.onClick()

  const done = (close: () => void) => () => {
    close()
    onChanged()
  }

  const ui = (
    <>
      <DiscrepancyModal order={discrepancyOrder} onClose={() => setDiscrepancyOrder(null)} onDone={done(() => setDiscrepancyOrder(null))} />
      <ItemsModal order={itemsOrder} onClose={() => setItemsOrder(null)} onDone={done(() => setItemsOrder(null))} />
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
        <Typography.Paragraph>Đơn sẽ bị ẩn khỏi danh sách nhưng vẫn được lưu để đối soát. Không thể hoàn tác.</Typography.Paragraph>
        <Input.TextArea rows={3} maxLength={500} showCount placeholder="Lý do hủy (không bắt buộc)" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} />
      </Modal>

      <Modal
        title={issueOrder ? `Báo thiếu / hỏng · đơn #${issueOrder.id}` : ''}
        open={issueOrder !== null}
        onCancel={() => setIssueOrder(null)}
        onOk={confirmIssue}
        okText="Gửi báo cáo"
        cancelText="Đóng"
        okButtonProps={{ danger: true, disabled: !issueNote.trim() }}
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
        destroyOnHidden
      >
        <Typography.Paragraph>Mô tả cụ thể món bị thiếu hoặc hỏng. Xưởng sẽ giao bù hoặc điều chỉnh số tiền trước khi chốt đơn.</Typography.Paragraph>
        <Input.TextArea
          rows={4}
          maxLength={500}
          showCount
          aria-label="Mô tả đồ bị thiếu hoặc hỏng"
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
          <Input type="datetime-local" aria-label="Hạn trả mới" value={dueValue} onChange={(e) => setDueValue(e.target.value)} />
          <Input.TextArea rows={2} maxLength={500} aria-label="Lý do đổi hạn" placeholder="Lý do (VD: khách đồng ý lùi hạn)" value={dueNote} onChange={(e) => setDueNote(e.target.value)} />
        </Space>
      </Modal>
    </>
  )

  return { actionsFor, runFromMenu, busyId, openDetail: setDetailId, ui }
}
