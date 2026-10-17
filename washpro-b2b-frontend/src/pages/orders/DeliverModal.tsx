import { useRef, useState } from 'react'
import { Modal, Typography } from 'antd'
import axiosClient from '../../api/axiosClient'
import PhotoPicker, { type PickedPhoto } from '../../components/PhotoPicker'
import SignaturePad from '../../components/SignaturePad'
import { uploadAttachments, type PendingAttachment } from '../../utils/attachments'
import { getErrorMessage } from '../../utils/format'
import { message } from '../../utils/message'
import type { Order } from '../../types'

/** Tài xế giao xong: khách ký nhận (hoặc chụp ảnh lúc giao) làm chứng từ, đơn hoàn thành. */
export default function DeliverModal({ order, onClose, onDone }: { order: Order | null; onClose: () => void; onDone: () => void }) {
  const [photos, setPhotos] = useState<PickedPhoto[]>([])
  const [signature, setSignature] = useState<Blob | null>(null)
  const [saving, setSaving] = useState(false)
  const uploaded = useRef(false)

  const files: PendingAttachment[] = photos.map((p) => ({ kind: 'DELIVERY_PHOTO', blob: p.blob }))
  if (signature) files.push({ kind: 'DELIVERY_SIGNATURE', blob: signature })

  const submit = async () => {
    if (!order) return
    setSaving(true)
    try {
      if (!uploaded.current) {
        const failed = await uploadAttachments(order.id, files)
        if (failed === files.length) {
          message.error('Chưa tải được chữ ký / ảnh, vui lòng thử lại')
          return
        }
        uploaded.current = true
      }
      await axiosClient.put(`/orders/${order.id}/delivered`)
      message.success(`Đã giao xong đơn #${order.id}`)
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={order ? `Giao xong · đơn #${order.id}` : ''}
      open={order !== null}
      onCancel={onClose}
      onOk={submit}
      okText="Xác nhận giao xong"
      cancelText="Hủy"
      okButtonProps={{ disabled: files.length === 0 && !uploaded.current }}
      confirmLoading={saving}
      className="sheet-modal"
      width={{ xs: '100%', md: 520 }}
      destroyOnHidden
      afterClose={() => {
        setPhotos([])
        setSignature(null)
        uploaded.current = false
      }}
    >
      {order && (
        <div className="proof-box">
          <Typography.Text>
            {order.customerName} · {order.totalBags} túi. Khách kiểm đồ rồi ký nhận; đơn sẽ hoàn thành và tính vào công nợ.
          </Typography.Text>
          <SignaturePad onChange={setSignature} label="Khách ký nhận đồ sạch" />
          <PhotoPicker value={photos} onChange={setPhotos} label="Chụp ảnh lúc giao" />
        </div>
      )}
    </Modal>
  )
}
