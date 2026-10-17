import { useRef, useState } from 'react'
import { Alert, Form, Input, InputNumber, Modal, Typography } from 'antd'
import axiosClient from '../../api/axiosClient'
import CatalogLines from '../../components/CatalogLines'
import PhotoPicker, { type PickedPhoto } from '../../components/PhotoPicker'
import SignaturePad from '../../components/SignaturePad'
import { uploadAttachments, type PendingAttachment } from '../../utils/attachments'
import { toItemRequest, useCatalog, type LineValue } from '../../utils/catalog'
import { getErrorMessage } from '../../utils/format'
import { message } from '../../utils/message'
import type { Order } from '../../types'

interface FormValues {
  totalBags: number
  description?: string
  items: LineValue[]
}

/**
 * Nhận đồ: tài xế kiểm số lượng cùng khách, khách ký (hoặc chụp ảnh túi đồ) thay cho bước khách xác nhận trên ứng dụng.
 * Quản lý xưởng dùng khi khách mang đồ đến xưởng (không bắt buộc chữ ký).
 */
export default function ReceiveModal({ order, atWorkshop, onClose, onDone }: { order: Order | null; atWorkshop: boolean; onClose: () => void; onDone: () => void }) {
  const [form] = Form.useForm<FormValues>()
  const [submitting, setSubmitting] = useState(false)
  const { catalog, loading: loadingCatalog } = useCatalog(order !== null)
  const [photos, setPhotos] = useState<PickedPhoto[]>([])
  const [signature, setSignature] = useState<Blob | null>(null)
  /** Ảnh / chữ ký đã tải lên trong lần mở này (gửi lại sau lỗi không tải trùng). */
  const uploaded = useRef(false)

  const submit = async (values: FormValues) => {
    if (!order) return
    const files: PendingAttachment[] = photos.map((p) => ({ kind: 'PICKUP_PHOTO', blob: p.blob }))
    if (signature) files.push({ kind: 'PICKUP_SIGNATURE', blob: signature })
    if (!atWorkshop && files.length === 0 && !uploaded.current) {
      message.error('Cần chữ ký của khách hoặc ảnh túi đồ để xác nhận số lượng')
      return
    }
    setSubmitting(true)
    try {
      if (files.length && !uploaded.current) {
        const failed = await uploadAttachments(order.id, files)
        if (failed === files.length && !atWorkshop) {
          message.error('Chưa tải được chữ ký / ảnh, vui lòng thử lại')
          return
        }
        if (failed) message.warning(`Chưa tải được ${failed} ảnh, có thể thêm lại trong Chi tiết đơn`)
        uploaded.current = true
      }
      await axiosClient.put(`/orders/${order.id}/receive`, {
        totalBags: values.totalBags,
        description: values.description?.trim() || null,
        items: values.items.map((l) => toItemRequest(l)),
      })
      message.success(`Đã nhận đồ đơn #${order.id}`)
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={order ? `${atWorkshop ? 'Nhận đồ tại xưởng' : 'Nhận đồ'} · đơn #${order.id}` : ''}
      open={order !== null}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Xác nhận đã nhận đủ"
      cancelText="Hủy"
      confirmLoading={submitting}
      width={{ xs: '100%', md: 720 }}
      className="sheet-modal"
      destroyOnHidden
      afterClose={() => {
        setPhotos([])
        setSignature(null)
        uploaded.current = false
      }}
    >
      {order && (
        <Form<FormValues>
          form={form}
          layout="vertical"
          onFinish={submit}
          initialValues={{ totalBags: order.estimatedBags ?? 1, items: [{ quantity: 1 }] }}
        >
          <Typography.Paragraph strong className="mb-2">
            {order.customerName}
          </Typography.Paragraph>
          {order.pickupNote && <Alert type="info" showIcon className="mb-3" title={`Khách dặn: ${order.pickupNote}`} />}
          <Form.Item name="totalBags" label="Số túi / bao đồ" rules={[{ required: true, message: 'Vui lòng nhập số túi' }]}>
            <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} inputMode="numeric" />
          </Form.Item>
          <Form.Item
            label="Đếm đồ theo từng loại"
            required
            extra="Kiểm cùng khách. Chăn ga, đồng phục tính theo kg thì nhập số cân (được lẻ, VD 12,5)."
            className="mb-4"
          >
            <CatalogLines name="items" catalog={catalog} loading={loadingCatalog} minLines={1} />
          </Form.Item>
          <Form.Item name="description" label="Ghi chú" rules={[{ max: 1000, message: 'Tối đa 1000 ký tự' }]}>
            <Input.TextArea rows={2} maxLength={1000} placeholder="Ví dụ: chia 2 túi nhỏ, 2 ga giường bị ố vàng, áo đầu bếp dính dầu..." />
          </Form.Item>
          <div className="proof-box">
            <Typography.Text strong>{atWorkshop ? 'Chữ ký / ảnh của khách (không bắt buộc)' : 'Khách ký xác nhận số lượng (hoặc chụp ảnh túi đồ)'}</Typography.Text>
            <SignaturePad onChange={setSignature} label="Khách ký xác nhận đã giao đủ đồ" />
            <PhotoPicker value={photos} onChange={setPhotos} label="Chụp ảnh túi đồ" />
          </div>
          <Typography.Text type="secondary">Đơn giá do xưởng áp theo bảng giá của khách hàng, tài xế không cần nhập.</Typography.Text>
        </Form>
      )}
    </Modal>
  )
}
