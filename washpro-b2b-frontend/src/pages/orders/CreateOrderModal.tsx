import { useEffect, useState } from 'react'
import { Alert, Form, Input, InputNumber, Modal, Select, Typography, message } from 'antd'
import axiosClient from '../../api/axiosClient'
import CatalogLines from '../../components/CatalogLines'
import { toItemRequest, useCatalog, type LineValue } from '../../utils/catalog'
import { getErrorMessage } from '../../utils/format'
import { CUSTOMER_TYPE_LABELS, type PickupRequest, type Restaurant } from '../../types'

interface Props {
  open: boolean
  onClose: () => void
  onDone: () => void
  /** Tạo đơn từ yêu cầu lấy đồ: khóa khách hàng và gắn yêu cầu vào đơn. */
  fromPickup?: PickupRequest | null
}

interface FormValues {
  restaurantId: number
  totalBags: number
  description?: string
  items: LineValue[]
}

export default function CreateOrderModal({ open, onClose, onDone, fromPickup }: Props) {
  const [form] = Form.useForm<FormValues>()
  const [restaurants, setRestaurants] = useState<Restaurant[]>([])
  const [loadingRestaurants, setLoadingRestaurants] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const { catalog, loading: loadingCatalog } = useCatalog(open)

  useEffect(() => {
    if (!open) return
    if (fromPickup) {
      form.setFieldsValue({ restaurantId: fromPickup.restaurantId, totalBags: fromPickup.estimatedBags ?? 1 })
    }
    setLoadingRestaurants(true)
    axiosClient
      .get<Restaurant[]>('/restaurants')
      .then((res) => setRestaurants(res.data.filter((r) => r.active)))
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được danh sách khách hàng')))
      .finally(() => setLoadingRestaurants(false))
  }, [open, fromPickup, form])

  const submit = async (values: FormValues) => {
    setSubmitting(true)
    try {
      await axiosClient.post('/orders', {
        restaurantId: values.restaurantId,
        totalBags: values.totalBags,
        description: values.description?.trim() || null,
        items: values.items.map((l) => toItemRequest(l)),
        pickupRequestId: fromPickup?.id ?? null,
      })
      message.success('Đã tạo đơn, đang chờ khách hàng xác nhận')
      form.resetFields()
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
      afterClose={() => form.resetFields()}
    >
      <Form<FormValues>
        form={form}
        layout="vertical"
        onFinish={submit}
        initialValues={{
          restaurantId: fromPickup?.restaurantId,
          totalBags: fromPickup?.estimatedBags ?? 1,
          items: [{ quantity: 1 }],
        }}
      >
        {fromPickup?.note && (
          <Alert type="info" showIcon className="mb-3" title={`Khách dặn: ${fromPickup.note}`} />
        )}
        <Form.Item name="restaurantId" label="Khách hàng" rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}>
          <Select
            disabled={!!fromPickup}
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="Chọn khách hàng"
            loading={loadingRestaurants}
            options={restaurants.map((r) => ({ value: r.id, label: `${r.name} (${CUSTOMER_TYPE_LABELS[r.customerType]})` }))}
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
        <Typography.Text type="secondary">Đơn giá do xưởng áp theo bảng giá của khách hàng, tài xế không cần nhập.</Typography.Text>
      </Form>
    </Modal>
  )
}
