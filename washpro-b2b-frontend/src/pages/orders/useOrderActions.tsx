import { useCallback, useState, type ReactNode } from 'react'
import { Input, Modal, Space, Typography } from 'antd'
import { message } from '../../utils/message'
import axiosClient from '../../api/axiosClient'
import PhotoPicker, { type PickedPhoto } from '../../components/PhotoPicker'
import { uploadAttachments } from '../../utils/attachments'
import AssignModal from './AssignModal'
import DeliverModal from './DeliverModal'
import ItemsModal from './ItemsModal'
import NoteModal from './NoteModal'
import OrderDrawer from './OrderDrawer'
import ReceiveModal from './ReceiveModal'
import RequestModal from './RequestModal'
import { getSession, isCustomerRole } from '../../utils/auth'
import { getErrorMessage, toLocalInput } from '../../utils/format'
import { isPickupStage, STATUS_META, type Order, type OrderStatus } from '../../types'

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
  /** Cần đủ đơn giá mới làm được (giặt xong / chốt). */
  needsPrice?: boolean
  primary?: boolean
}

/** Các bước Quản lý xưởng bấm để đẩy đơn đi (phân công tài xế là thao tác riêng). */
const ADMIN_NEXT: Partial<Record<OrderStatus, AdminStep[]>> = {
  RECEIVED: [{ status: 'WASHING', label: 'Bắt đầu giặt', confirm: 'Đồ đã về xưởng, đã kiểm số túi. Bắt đầu giặt?', primary: true }],
  WASHING: [{ status: 'WASHED', label: 'Đã giặt xong', confirm: 'Đã giặt xong và kiểm lại số lượng? Đơn sẽ chờ tài xế giao.', needsPrice: true, primary: true }],
  DELIVERING: [
    { status: 'COMPLETED', label: 'Chốt thay khách', confirm: 'Tài xế / khách chưa xác nhận đã giao. Xưởng chốt hoàn thành?', needsPrice: true },
  ],
  DELIVERY_ISSUE: [
    { status: 'DELIVERING', label: 'Giao bù', confirm: 'Giao bù phần thiếu/hỏng cho khách?', primary: true },
    { status: 'COMPLETED', label: 'Chốt hoàn thành', confirm: 'Đã xử lý xong sự cố (đã điều chỉnh chi tiết/giá nếu cần)?', needsPrice: true },
  ],
}

/**
 * Toàn bộ thao tác trên đơn theo vai trò + các hộp thoại đi kèm, từ yêu cầu lấy đồ đến khi giao xong
 * (nhận việc, phân công, nhận đồ, giặt, giao, hủy, ghi chú...). Dùng chung cho trang Đơn hàng, trang Lấy & giao
 * và trang chủ từng vai trò để thao tác được ngay tại chỗ. Gọi onChanged sau mỗi thao tác thành công.
 */
export function useOrderActions(onChanged: () => void) {
  const session = getSession()
  const role = session?.role
  const me = session?.username
  const isDriver = role === 'DRIVER'
  const isCustomer = role ? isCustomerRole(role) : false
  const isAdmin = role === 'WORKSHOP_ADMIN'
  const isAccountant = role === 'WORKSHOP_ACCOUNTANT'

  const [modal, modalHolder] = Modal.useModal()
  const [busyId, setBusyId] = useState<number | null>(null)
  const [itemsOrder, setItemsOrder] = useState<Order | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [cancelOrder, setCancelOrder] = useState<Order | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [issueOrder, setIssueOrder] = useState<Order | null>(null)
  const [issueNote, setIssueNote] = useState('')
  const [issuePhotos, setIssuePhotos] = useState<PickedPhoto[]>([])
  const [dueOrder, setDueOrder] = useState<Order | null>(null)
  const [dueValue, setDueValue] = useState('')
  const [dueNote, setDueNote] = useState('')
  const [noteOrderId, setNoteOrderId] = useState<number | null>(null)
  const [assignOrder, setAssignOrder] = useState<Order | null>(null)
  const [receiveOrder, setReceiveOrder] = useState<Order | null>(null)
  const [deliverOrder, setDeliverOrder] = useState<Order | null>(null)
  const [creating, setCreating] = useState(false)
  const openCreate = useCallback(() => setCreating(true), [])

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
    await run(
      target,
      () => axiosClient.delete(`/orders/${target.id}`, { params: { reason: cancelReason.trim() || undefined } }),
      isPickupStage(target) ? 'Đã hủy yêu cầu' : `Đã hủy đơn #${target.id}`,
    )
    setCancelReason('')
  }

  const confirmIssue = async () => {
    if (!issueOrder || !issueNote.trim()) return
    const target = issueOrder
    setIssueOrder(null)
    const photos = issuePhotos
    await run(
      target,
      async () => {
        await axiosClient.put(`/orders/${target.id}/receipt-issue`, { note: issueNote.trim() })
        const failed = await uploadAttachments(target.id, photos.map((p) => ({ kind: 'ISSUE_PHOTO' as const, blob: p.blob })))
        if (failed) message.warning(`Chưa tải được ${failed} ảnh, có thể thêm lại trong Chi tiết đơn`)
      },
      'Đã báo sự cố, xưởng sẽ liên hệ xử lý',
    )
    setIssueNote('')
    setIssuePhotos([])
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

  const release = (o: Order, what: string): OrderAction => ({
    key: 'release',
    label: 'Trả lại việc',
    confirm: { title: `Trả lại việc ${what} cho tài xế khác?`, okText: 'Trả lại' },
    onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/release`), 'Đã trả lại việc'),
  })

  /** Các thao tác theo vai trò và trạng thái đơn; thao tác chính (primary) xếp trước. */
  const actionsFor = (o: Order): OrderAction[] => {
    const pickup = isPickupStage(o)
    const actions: OrderAction[] = []

    // ----- Khách hàng -----
    if (isCustomer && o.status === 'DELIVERING') {
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

    // ----- Tài xế -----
    if (isDriver) {
      if (o.status === 'PICKUP_PENDING') {
        actions.push({ key: 'accept', label: 'Nhận việc', primary: true, onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/accept`), 'Đã nhận việc') })
      }
      if (o.status === 'PICKUP_ASSIGNED' && o.driverUsername === me) {
        actions.push({ key: 'receive', label: 'Đã nhận đồ', primary: true, onClick: () => setReceiveOrder(o) }, release(o, 'lấy đồ'))
      }
      if (o.status === 'WASHED') {
        actions.push({ key: 'accept', label: 'Nhận giao', primary: true, onClick: () => run(o, () => axiosClient.put(`/orders/${o.id}/accept`), 'Đã nhận giao') })
      }
      if (o.status === 'DELIVERING' && o.deliveryDriverUsername === me) {
        actions.push({ key: 'deliver', label: 'Giao xong', primary: true, onClick: () => setDeliverOrder(o) }, release(o, 'giao trả'))
      }
    }

    // ----- Quản lý xưởng -----
    if (isAdmin) {
      if (pickup) {
        actions.push(
          { key: 'assign', label: o.driverId ? 'Đổi tài xế' : 'Phân công tài xế', primary: !o.driverId, onClick: () => setAssignOrder(o) },
          { key: 'receive', label: 'Nhận đồ tại xưởng', onClick: () => setReceiveOrder(o) },
        )
      }
      if (o.status === 'WASHED') {
        actions.push({ key: 'assign', label: 'Phân công giao', primary: true, onClick: () => setAssignOrder(o) })
      }
      if (o.status === 'DELIVERING' || o.status === 'DELIVERY_ISSUE') {
        actions.push({ key: 'assign', label: 'Đổi tài xế giao', onClick: () => setAssignOrder(o) })
      }
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
    if ((isAdmin || isAccountant) && !pickup && o.status !== 'COMPLETED') {
      actions.push({ key: 'items', label: isAccountant && !o.billable ? 'Nhập đơn giá' : 'Chi tiết & giá', primary: isAccountant && !o.billable, onClick: () => setItemsOrder(o) })
    }
    if (isAdmin || isAccountant || isDriver) {
      actions.push({ key: 'note', label: 'Ghi chú', onClick: () => setNoteOrderId(o.id) })
    }
    if (isAdmin && !pickup && o.status !== 'COMPLETED') {
      actions.push({ key: 'due', label: 'Đổi hạn trả', onClick: () => openDue(o) })
    }
    if ((isAdmin && o.status !== 'COMPLETED') || (isCustomer && pickup)) {
      actions.push({ key: 'cancel', label: pickup ? 'Hủy yêu cầu' : 'Hủy đơn', danger: true, onClick: () => setCancelOrder(o) })
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

  const cancelPickup = cancelOrder ? isPickupStage(cancelOrder) : false

  const ui = (
    <>
      <ItemsModal order={itemsOrder} onClose={() => setItemsOrder(null)} onDone={done(() => setItemsOrder(null))} />
      <OrderDrawer orderId={detailId} onClose={() => setDetailId(null)} />
      <NoteModal orderId={noteOrderId} onClose={() => setNoteOrderId(null)} />
      <AssignModal order={assignOrder} onClose={() => setAssignOrder(null)} onDone={done(() => setAssignOrder(null))} />
      <ReceiveModal order={receiveOrder} atWorkshop={isAdmin} onClose={() => setReceiveOrder(null)} onDone={done(() => setReceiveOrder(null))} />
      <DeliverModal order={deliverOrder} onClose={() => setDeliverOrder(null)} onDone={done(() => setDeliverOrder(null))} />
      <RequestModal open={creating} forWorkshop={isAdmin} onClose={() => setCreating(false)} onDone={done(() => setCreating(false))} />
      {modalHolder}

      <Modal
        title={cancelOrder ? (cancelPickup ? `Hủy yêu cầu lấy đồ #${cancelOrder.id}?` : `Hủy đơn #${cancelOrder.id}?`) : ''}
        open={cancelOrder !== null}
        onCancel={() => setCancelOrder(null)}
        onOk={confirmCancel}
        okText={cancelPickup ? 'Hủy yêu cầu' : 'Hủy đơn'}
        cancelText="Đóng"
        okButtonProps={{ danger: true }}
        destroyOnHidden
      >
        {!cancelPickup && <Typography.Paragraph>Đơn sẽ bị ẩn khỏi danh sách nhưng vẫn được lưu để đối soát. Không thể hoàn tác.</Typography.Paragraph>}
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          aria-label="Lý do hủy"
          placeholder="Lý do (không bắt buộc)"
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
        />
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
        <div style={{ marginTop: 12 }}>
          <PhotoPicker value={issuePhotos} onChange={setIssuePhotos} label="Chụp ảnh đồ thiếu / hỏng" />
        </div>
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
        <Space orientation="vertical" style={{ width: '100%' }}>
          <Input type="datetime-local" aria-label="Hạn trả mới" value={dueValue} onChange={(e) => setDueValue(e.target.value)} />
          <Input.TextArea rows={2} maxLength={500} aria-label="Lý do đổi hạn" placeholder="Lý do (VD: khách đồng ý lùi hạn)" value={dueNote} onChange={(e) => setDueNote(e.target.value)} />
        </Space>
      </Modal>
    </>
  )

  return { actionsFor, runFromMenu, busyId, openDetail: setDetailId, openCreate, canCreate: isCustomer || isAdmin, ui }
}
