import { useEffect, useState } from 'react'
import { Alert, Modal, Skeleton } from 'antd'
import axiosClient from '../../api/axiosClient'
import { getErrorMessage } from '../../utils/format'
import OrderNotes from './OrderNotes'
import type { Order, OrderNote } from '../../types'

/** Ghi chú nhanh ngay từ danh sách đơn (không phải mở chi tiết). */
export default function NoteModal({ orderId, onClose }: { orderId: number | null; onClose: () => void }) {
  const [notes, setNotes] = useState<OrderNote[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (orderId === null) return
    let ignore = false
    setNotes(null)
    setError(null)
    axiosClient
      .get<Order>(`/orders/${orderId}`)
      .then(({ data }) => !ignore && setNotes(data.notes ?? []))
      .catch((e) => !ignore && setError(getErrorMessage(e, 'Không tải được ghi chú')))
    return () => {
      ignore = true
    }
  }, [orderId])

  return (
    <Modal
      title={orderId !== null ? `Ghi chú nội bộ · đơn #${orderId}` : ''}
      open={orderId !== null}
      onCancel={onClose}
      footer={null}
      className="sheet-modal"
      width={{ xs: '100%', md: 520 }}
      destroyOnHidden
    >
      {error && <Alert type="error" showIcon title={error} />}
      {!notes && !error && <Skeleton active paragraph={{ rows: 3 }} />}
      {notes && orderId !== null && <OrderNotes orderId={orderId} notes={notes} onChange={setNotes} />}
    </Modal>
  )
}
