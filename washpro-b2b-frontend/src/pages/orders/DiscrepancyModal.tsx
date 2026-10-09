import { useState } from 'react'
import { Alert, Divider, Form, Input, InputNumber, Modal, Table, Typography, message } from 'antd'
import axiosClient from '../../api/axiosClient'
import CatalogLines from '../../components/CatalogLines'
import { catalogLabel, formatQuantity, toItemRequest, useCatalog, type LineValue } from '../../utils/catalog'
import { getErrorMessage } from '../../utils/format'
import { UNIT_META, type Order, type OrderItem } from '../../types'

interface Props {
  order: Order | null
  onClose: () => void
  onDone: () => void
}

interface FormValues {
  totalBags: number
  /** Số lượng thực tế theo id dòng. */
  actual: Record<number, number>
  added?: LineValue[]
  note: string
}

export default function DiscrepancyModal({ order, onClose, onDone }: Props) {
  const [form] = Form.useForm<FormValues>()
  const [submitting, setSubmitting] = useState(false)
  const { catalog, loading } = useCatalog(order !== null)

  const submit = async (values: FormValues) => {
    if (!order) return
    setSubmitting(true)
    try {
      const counts = order.items.map((i) => ({ itemId: i.id, quantity: values.actual?.[i.id] ?? i.quantity }))
      const added = (values.added ?? []).map((l) => {
        const { servicePriceId, itemName, unit, quantity } = toItemRequest(l)
        return { itemId: null, servicePriceId, itemName, unit, quantity }
      })
      await axiosClient.put(`/orders/${order.id}/discrepancy`, {
        totalBags: values.totalBags,
        items: [...counts, ...added],
        note: values.note.trim(),
      })
      message.success('Đã ghi nhận sai lệch')
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={order ? `Báo sai lệch đơn #${order.id}` : 'Báo sai lệch'}
      open={order !== null}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Gửi báo sai lệch"
      cancelText="Hủy"
      okButtonProps={{ danger: true }}
      confirmLoading={submitting}
      width={{ xs: '100%', md: 760 }}
      className="sheet-modal"
      destroyOnHidden
    >
      {order && (
        <Form<FormValues>
          form={form}
          layout="vertical"
          onFinish={submit}
          initialValues={{
            totalBags: order.totalBags,
            actual: Object.fromEntries(order.items.map((i) => [i.id, i.quantity])),
          }}
        >
          <Alert
            type="info"
            showIcon
            className="mb-4"
            title="Sửa cột “Thực tế” cho những loại đồ bị lệch (nhập 0 nếu không có). Lịch sử thay đổi được lưu lại để đối soát."
          />
          <Form.Item name="totalBags" label="Số túi / bao thực tế" rules={[{ required: true, message: 'Vui lòng nhập số túi' }]}>
            <InputNumber min={1} max={10000} precision={0} style={{ width: 200 }} />
          </Form.Item>

          <Table<OrderItem>
            size="small"
            rowKey="id"
            pagination={false}
            dataSource={order.items}
            scroll={{ x: 'max-content' }}
            columns={[
              { title: 'Loại đồ', key: 'name', render: (_: unknown, i) => catalogLabel(i) },
              { title: 'Đã ghi', key: 'qty', align: 'right', render: (_: unknown, i) => formatQuantity(i.quantity, i.unit) },
              {
                title: 'Thực tế',
                key: 'actual',
                width: 160,
                render: (_: unknown, i) => {
                  const decimal = UNIT_META[i.unit].decimal
                  return (
                    <Form.Item name={['actual', i.id]} rules={[{ required: true, message: 'Nhập SL' }]} style={{ margin: 0 }}>
                      <InputNumber min={0} max={100000} precision={decimal ? 2 : 0} suffix={UNIT_META[i.unit].label} style={{ width: 140 }} />
                    </Form.Item>
                  )
                },
              },
            ]}
          />

          <Divider titlePlacement="start" plain>
            Có loại đồ bị sót?
          </Divider>
          <CatalogLines name="added" catalog={catalog} loading={loading} addLabel="Thêm loại đồ chưa có trong đơn" />

          <Form.Item
            name="note"
            label="Lý do sai lệch"
            rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập lý do' }]}
            className="mt-4"
          >
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="Ví dụ: thiếu 2 vỏ gối, thừa 1 bộ đồng phục bếp..." />
          </Form.Item>
          <Typography.Text type="secondary">Sau khi báo, khách hàng xác nhận lại hoặc Quản lý xưởng mở lại đơn.</Typography.Text>
        </Form>
      )}
    </Modal>
  )
}
