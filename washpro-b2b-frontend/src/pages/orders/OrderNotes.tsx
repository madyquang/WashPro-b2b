import { useState } from 'react'
import { Button, Empty, Input, Typography } from 'antd'
import axiosClient from '../../api/axiosClient'
import { formatDateTime, getErrorMessage } from '../../utils/format'
import { message } from '../../utils/message'
import { ROLE_LABELS, type OrderNote } from '../../types'

/** Ghi chú nội bộ trên đơn: danh sách (cũ trước) + ô thêm ghi chú. Khách hàng không thấy phần này. */
export default function OrderNotes({ orderId, notes, onChange }: { orderId: number; notes: OrderNote[]; onChange: (notes: OrderNote[]) => void }) {
  const [draft, setDraft] = useState('')
  const [saving, setSaving] = useState(false)

  const add = async () => {
    const body = draft.trim()
    if (!body) return
    setSaving(true)
    try {
      const { data } = await axiosClient.post<OrderNote[]>(`/orders/${orderId}/notes`, { body })
      onChange(data)
      setDraft('')
      message.success('Đã lưu ghi chú')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="order-notes">
      {notes.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có ghi chú" />
      ) : (
        <ul className="note-list">
          {notes.map((n) => (
            <li key={n.id}>
              <div className="pre-wrap">{n.body}</div>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {n.author} ({ROLE_LABELS[n.authorRole]}) · {formatDateTime(n.createdAt)}
              </Typography.Text>
            </li>
          ))}
        </ul>
      )}
      <Input.TextArea
        rows={2}
        maxLength={1000}
        showCount
        aria-label="Ghi chú mới"
        placeholder="VD: đã gọi khách, khách hẹn bổ sung 2 khăn vào ngày mai..."
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
      />
      <Button type="primary" className="mt-2" loading={saving} disabled={!draft.trim()} onClick={add}>
        Thêm ghi chú
      </Button>
    </div>
  )
}
