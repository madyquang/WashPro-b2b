import { useEffect, useState } from 'react'
import { Alert, Collapse, Form, Input, InputNumber, Modal, Select, Typography } from 'antd'
import { message } from '../../utils/message'
import axiosClient from '../../api/axiosClient'
import CatalogLines from '../../components/CatalogLines'
import PhotoPicker, { type PickedPhoto } from '../../components/PhotoPicker'
import SignaturePad from '../../components/SignaturePad'
import { uploadAttachments, type PendingAttachment } from '../../utils/attachments'
import { toItemRequest, useCatalog, type LineValue } from '../../utils/catalog'
import { getErrorMessage } from '../../utils/format'
import { CUSTOMER_TYPE_LABELS, type Customer, type Order, type PickupRequest } from '../../types'

interface Props {
  open: boolean
  onClose: () => void
  onDone: () => void
  /** Tạo đơn từ yêu cầu lấy đồ: khóa khách hàng và gắn yêu cầu vào đơn. */
  fromPickup?: PickupRequest | null
}

interface FormValues {
  customerId: number
  totalBags: number
  description?: string
  items: LineValue[]
}

export default function CreateOrderModal({ open, onClose, onDone, fromPickup }: Props) {
  const [form] = Form.useForm<FormValues>()
  const [customers, setCustomers] = useState<Customer[]>([])
  const [loadingCustomers, setLoadingCustomers] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const { catalog, loading: loadingCatalog } = useCatalog(open)
  const [photos, setPhotos] = useState<PickedPhoto[]>([])
  const [signature, setSignature] = useState<Blob | null>(null)

  useEffect(() => {
    if (!open) return
    if (fromPickup) {
      form.setFieldsValue({ customerId: fromPickup.customerId, totalBags: fromPickup.estimatedBags ?? 1 })
    }
    setLoadingCustomers(true)
    axiosClient
      .get<Customer[]>('/customers')
      .then((res) => setCustomers(res.data.filter((r) => r.active)))
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được danh sách khách hàng')))
      .finally(() => setLoadingCustomers(false))
  }, [open, fromPickup, form])

  const submit = async (values: FormValues) => {
    setSubmitting(true)
    try {
      const { data: order } = await axiosClient.post<Order>('/orders', {
        customerId: values.customerId,
        totalBags: values.totalBags,
        description: values.description?.trim() || null,
        items: values.items.map((l) => toItemRequest(l)),
        pickupRequestId: fromPickup?.id ?? null,
      })
      // Ảnh / chữ ký không bắt buộc: lỗi tải ảnh không làm hỏng đơn đã tạo
      const files: PendingAttachment[] = photos.map((p) => ({ kind: 'PICKUP_PHOTO', blob: p.blob }))
      if (signature) files.push({ kind: 'PICKUP_SIGNATURE', blob: signature })
      const failed = files.length ? await uploadAttachments(order.id, files) : 0
      if (failed) message.warning(`Đã tạo đơn #${order.id} nhưng chưa tải được ${failed} ảnh. Có thể thêm lại trong Chi tiết đơn`)
      else message.success('Đã tạo đơn, đang chờ khách hàng xác nhận')
      form.resetFields()
      setPhotos([])
      setSignature(null)
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={fromPickup ? `Tạo đơn từ yêu cầu lấy đồ #${fromPickup.id}` : 'Tạo đơn giặt mới'}
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Tạo đơn"
      cancelText="Hủy"
      confirmLoading={submitting}
      width={{ xs: '100%', md: 720 }}
      className="sheet-modal"
      destroyOnHidden
      afterClose={() => {
        form.resetFields()
        setPhotos([])
        setSignature(null)
      }}
    >
      <Form<FormValues>
        form={form}
        layout="vertical"
        onFinish={submit}
        initialValues={{
          customerId: fromPickup?.customerId,
          totalBags: fromPickup?.estimatedBags ?? 1,
          items: [{ quantity: 1 }],
        }}
      >
        {fromPickup?.note && (
          <Alert type="info" showIcon className="mb-3" title={`Khách dặn: ${fromPickup.note}`} />
        )}
        <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}>
          <Select
            disabled={!!fromPickup}
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="Chọn khách hàng"
            loading={loadingCustomers}
            options={customers.map((r) => ({ value: r.id, label: `${r.name} (${CUSTOMER_TYPE_LABELS[r.customerType]})` }))}
            notFoundContent="Chưa có khách hàng nào đang hợp tác"
          />
        </Form.Item>
        <Form.Item name="totalBags" label="Số túi / bao đồ" rules={[{ required: true, message: 'Vui lòng nhập số túi' }]}>
          <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} inputMode="numeric" />
        </Form.Item>

        <Form.Item
          label="Đếm đồ theo từng loại"
          required
          extra="Chăn ga, đồng phục tính theo kg thì nhập số cân (được lẻ, VD 12,5). Khách hàng sẽ đối chiếu từng dòng khi xác nhận."
          className="mb-4"
        >
          <CatalogLines name="items" catalog={catalog} loading={loadingCatalog} minLines={1} />
        </Form.Item>

        <Form.Item name="description" label="Ghi chú" rules={[{ max: 1000, message: 'Tối đa 1000 ký tự' }]}>
          <Input.TextArea rows={2} maxLength={1000} placeholder="Ví dụ: 2 ga giường bị ố vàng, áo đầu bếp dính dầu..." />
        </Form.Item>
        <Collapse
          className="mb-3"
          items={[
            {
              key: 'proof',
              label: `Ảnh & chữ ký khách (không bắt buộc)${photos.length || signature ? ` · đã có ${photos.length} ảnh${signature ? ', chữ ký' : ''}` : ''}`,
              children: (
                <div style={{ display: 'grid', gap: 12 }}>
                  <PhotoPicker value={photos} onChange={setPhotos} label="Chụp ảnh túi đồ" />
                  <SignaturePad onChange={setSignature} label="Khách ký xác nhận giao đồ" />
                </div>
              ),
            },
          ]}
        />
        <Typography.Text type="secondary">Đơn giá do xưởng áp theo bảng giá của khách hàng, tài xế không cần nhập.</Typography.Text>
      </Form>
    </Modal>
  )
}
