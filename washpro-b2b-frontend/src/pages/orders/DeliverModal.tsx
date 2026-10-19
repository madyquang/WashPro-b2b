import { useRef, useState } from 'react'
import { Collapse, Modal, Typography } from 'antd'
import axiosClient from '../../api/axiosClient'
import PhotoPicker, { type PickedPhoto } from '../../components/PhotoPicker'
import SignaturePad from '../../components/SignaturePad'
import { uploadAttachments, type PendingAttachment } from '../../utils/attachments'
import { getErrorMessage } from '../../utils/format'
import { message } from '../../utils/message'
import type { Order } from '../../types'

/** Tài xế giao xong: chụp ảnh lúc giao (bắt buộc), khách ký không bắt buộc; đơn hoàn thành. */
export default function DeliverModal({ order, onClose, onDone }: { order: Order | null; onClose: () => void; onDone: () => void }) {
  const [photos, setPhotos] = useState<PickedPhoto[]>([])
  const [signature, setSignature] = useState<Blob | null>(null)
  const [saving, setSaving] = useState(false)
  const uploaded = useRef(false)

  const submit = async () => {
    if (!order) return
    const shots: PendingAttachment[] = photos.map((p) => ({ kind: 'DELIVERY_PHOTO', blob: p.blob }))
    setSaving(true)
    try {
      if (!uploaded.current) {
        if ((await uploadAttachments(order.id, shots)) === shots.length) {
          message.error('Chưa tải được ảnh, vui lòng thử lại')
          return
        }
        // Chữ ký không bắt buộc: tải lỗi cũng không chặn giao xong
        if (signature) await uploadAttachments(order.id, [{ kind: 'DELIVERY_SIGNATURE', blob: signature }])
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
      okButtonProps={{ disabled: photos.length === 0 && !uploaded.current }}
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
            {order.customerName} · {order.totalBags} túi. Chụp ảnh lúc giao; đơn sẽ hoàn thành và tính vào công nợ.
          </Typography.Text>
          <Typography.Text strong>Ảnh lúc giao (bắt buộc)</Typography.Text>
          <PhotoPicker value={photos} onChange={setPhotos} label="Chụp ảnh lúc giao" />
          <Collapse
            size="small"
            items={[
              {
                key: 'sign',
                label: `Khách ký nhận (không bắt buộc)${signature ? ' · đã ký' : ''}`,
                children: <SignaturePad onChange={setSignature} label="Khách ký nhận đồ sạch" />,
              },
            ]}
          />
        </div>
      )}
    </Modal>
  )
}
