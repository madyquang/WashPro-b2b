import { useEffect, useMemo, useState } from 'react'
import { Form, Modal, Typography, message } from 'antd'
import axiosClient from '../../api/axiosClient'
import CatalogLines from '../../components/CatalogLines'
import { CUSTOM_ITEM, toItemRequest, type LineValue } from '../../utils/catalog'
import { formatCurrency, getErrorMessage } from '../../utils/format'
import type { CatalogItem, CustomerPriceRow, Order } from '../../types'

interface Props {
  order: Order | null
  onClose: () => void
  onDone: () => void
}

interface FormValues {
  items: LineValue[]
}

export default function ItemsModal({ order, onClose, onDone }: Props) {
  const [form] = Form.useForm<FormValues>()
  const [prices, setPrices] = useState<CustomerPriceRow[]>([])
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const watched = Form.useWatch('items', form) as LineValue[] | undefined

  useEffect(() => {
    if (!order) return
    setLoading(true)
    axiosClient
      .get<CustomerPriceRow[]>(`/prices/customers/${order.restaurantId}`)
      .then((res) => setPrices(res.data))
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được bảng giá của khách hàng')))
      .finally(() => setLoading(false))
  }, [order])

  // Mục đang áp dụng, cộng các mục đã ẩn nhưng đơn này vẫn đang dùng
  const catalog: CatalogItem[] = useMemo(() => {
    const used = new Set(order?.items.map((i) => i.servicePriceId) ?? [])
    return prices
      .filter((p) => p.active || used.has(p.servicePriceId))
      .map((p) => ({
        id: p.servicePriceId,
        name: p.active ? p.name : `${p.name} (đã ẩn)`,
        category: p.category,
        serviceType: p.serviceType,
        unit: p.unit,
      }))
  }, [prices, order])

  const priceOf = (id: number) => prices.find((p) => p.servicePriceId === id)?.effectivePrice

  const total = (watched ?? []).reduce((sum, l) => sum + (l?.quantity ?? 0) * (l?.unitPrice ?? 0), 0)

  const submit = async (values: FormValues) => {
    if (!order) return
    setSubmitting(true)
    try {
      await axiosClient.put(`/orders/${order.id}/items`, {
        items: values.items.map((l) => toItemRequest(l, true)),
      })
      message.success('Đã lưu chi tiết đồ giặt')
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  const initialItems: LineValue[] =
    order && order.items.length > 0
      ? order.items.map((i) => ({
          servicePriceId: i.servicePriceId ?? CUSTOM_ITEM,
          itemName: i.servicePriceId ? undefined : i.itemName,
          unit: i.unit,
          quantity: i.quantity,
          unitPrice: i.unitPrice,
        }))
      : [{ quantity: 1 }]

  return (
    <Modal
      title={order ? `Chi tiết đồ giặt & đơn giá · đơn #${order.id} · ${order.restaurantName}` : ''}
      open={order !== null}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={submitting}
      width={860}
      destroyOnHidden
    >
      {order && (
        <Form<FormValues> form={form} layout="vertical" onFinish={submit} initialValues={{ items: initialItems }}>
          <Typography.Paragraph type="secondary">
            Chọn loại đồ để tự điền đơn giá theo bảng giá của khách hàng này (có giá hợp đồng riêng thì dùng giá riêng). Có thể
            sửa số lượng sau khi cân/đếm lại ở xưởng. Đơn chỉ hoàn thành được khi mọi dòng đã có đơn giá.
          </Typography.Paragraph>
          <CatalogLines name="items" catalog={catalog} loading={loading} withPrice priceOf={priceOf} minLines={1} addLabel="Thêm dòng" />
          <div style={{ textAlign: 'right', marginTop: 16 }}>
            <Typography.Text strong>Tạm tính: {formatCurrency(total)}</Typography.Text>
          </div>
        </Form>
      )}
    </Modal>
  )
}
