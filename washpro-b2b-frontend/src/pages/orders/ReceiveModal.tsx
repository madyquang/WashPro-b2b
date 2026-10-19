import { useRef, useState } from 'react'
import { Alert, Collapse, Form, Input, InputNumber, Modal, Table, Typography } from 'antd'
import axiosClient from '../../api/axiosClient'
import CatalogLines from '../../components/CatalogLines'
import PhotoPicker, { type PickedPhoto } from '../../components/PhotoPicker'
import SignaturePad from '../../components/SignaturePad'
import { uploadAttachments, type PendingAttachment } from '../../utils/attachments'
import { catalogLabel, toItemRequest, useCatalog, type LineValue } from '../../utils/catalog'
import { getErrorMessage } from '../../utils/format'
import { message } from '../../utils/message'
import { UNIT_META, type DeclaredItem, type Order } from '../../types'

interface FormValues {
  totalBags: number
  description?: string
  /** Số thực nhận của từng dòng khách khai (cùng thứ tự với order.declaredItems). */
  received?: (number | null)[]
  /** Món khách không khai (hoặc mọi món khi khách không khai trước). */
  items: LineValue[]
}

const plain = (n: number) => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(n)

/**
 * Nhận đồ. Khách đã khai: số khai giữ nguyên, tài xế ghi số thực nhận ở cột bên cạnh (điền sẵn bằng số khai),
 * hệ thống tự tính chênh lệch. Tài xế bắt buộc chụp ảnh túi đồ, khách ký không bắt buộc.
 * Quản lý xưởng dùng khi khách mang đồ đến xưởng (không bắt buộc ảnh).
 */
export default function ReceiveModal({ order, atWorkshop, onClose, onDone }: { order: Order | null; atWorkshop: boolean; onClose: () => void; onDone: () => void }) {
  const [form] = Form.useForm<FormValues>()
  const [submitting, setSubmitting] = useState(false)
  const { catalog, loading: loadingCatalog } = useCatalog(order !== null)
  const [photos, setPhotos] = useState<PickedPhoto[]>([])
  const [signature, setSignature] = useState<Blob | null>(null)
  /** Ảnh / chữ ký đã tải lên trong lần mở này (gửi lại sau lỗi không tải trùng). */
  const uploaded = useRef(false)
  const received = (Form.useWatch('received', form) as (number | null)[] | undefined) ?? []

  const declared = order?.declaredItems ?? []
  const hasDeclared = declared.length > 0

  const submit = async (values: FormValues) => {
    if (!order) return
    if (!atWorkshop && photos.length === 0 && !uploaded.current) {
      message.error('Cần chụp ít nhất một ảnh túi đồ')
      return
    }
    const fromDeclared = declared
      .map((d, i) => ({ d, qty: values.received?.[i] ?? 0 }))
      .filter(({ qty }) => qty > 0)
      .map(({ d, qty }) => ({ servicePriceId: d.servicePriceId, itemName: d.servicePriceId ? null : d.itemName, unit: d.servicePriceId ? null : d.unit, quantity: qty }))
    const extra = (values.items ?? []).map((l) => toItemRequest(l))
    if (fromDeclared.length + extra.length === 0) {
      message.error('Chưa có món nào được nhận')
      return
    }
    const files: PendingAttachment[] = photos.map((p) => ({ kind: 'PICKUP_PHOTO', blob: p.blob }))
    if (signature) files.push({ kind: 'PICKUP_SIGNATURE', blob: signature })
    setSubmitting(true)
    try {
      if (files.length && !uploaded.current) {
        const failed = await uploadAttachments(order.id, files)
        if (failed === files.length && !atWorkshop) {
          message.error('Chưa tải được ảnh, vui lòng thử lại')
          return
        }
        if (failed) message.warning(`Chưa tải được ${failed} ảnh, có thể thêm lại trong Chi tiết đơn`)
        uploaded.current = true
      }
      await axiosClient.put(`/orders/${order.id}/receive`, {
        totalBags: values.totalBags,
        description: values.description?.trim() || null,
        items: [...fromDeclared, ...extra],
      })
      message.success(`Đã nhận đồ đơn #${order.id}`)
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  const diffCell = (d: DeclaredItem, i: number) => {
    const diff = (received[i] ?? 0) - d.quantity
    if (diff === 0) return <Typography.Text type="success">0</Typography.Text>
    return (
      <Typography.Text type="danger" strong>
        {diff > 0 ? '+' : ''}
        {plain(diff)}
      </Typography.Text>
    )
  }

  return (
    <Modal
      title={order ? `${atWorkshop ? 'Nhận đồ tại xưởng' : 'Nhận đồ'} · đơn #${order.id}` : ''}
      open={order !== null}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Xác nhận đã nhận"
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
          name="receive"
          layout="vertical"
          onFinish={submit}
          initialValues={{
            totalBags: order.estimatedBags ?? 1,
            // Điền sẵn số thực nhận bằng số khai; tài xế chỉ sửa ô lệch
            received: declared.map((d) => d.quantity),
            items: hasDeclared ? [] : [{ quantity: 1 }],
          }}
        >
          <Typography.Paragraph strong className="mb-2">
            Đơn #{order.id} · {order.customerName}
            <Typography.Text type="secondary"> (ghi mã đơn lên túi)</Typography.Text>
          </Typography.Paragraph>
          {order.pickupNote && <Alert type="info" showIcon className="mb-3" title={`Khách dặn: ${order.pickupNote}`} />}

          {hasDeclared && (
            <Form.Item label="Soát đồ theo số khách khai" className="mb-3">
              <Table<DeclaredItem>
                size="small"
                pagination={false}
                rowKey="id"
                dataSource={declared}
                className="declared-table"
                columns={[
                  { title: 'Loại đồ', key: 'name', render: (_: unknown, d) => catalogLabel(d) },
                  { title: 'Khách khai', key: 'declared', align: 'right', render: (_: unknown, d) => `${plain(d.quantity)} ${UNIT_META[d.unit].label}` },
                  {
                    title: 'Thực nhận',
                    key: 'received',
                    width: 120,
                    render: (_: unknown, d, i) => (
                      <Form.Item name={['received', i]} noStyle rules={[{ required: true, message: 'Nhập số thực nhận' }]}>
                        <InputNumber
                          aria-label={`Thực nhận ${d.itemName}`}
                          min={0}
                          max={100000}
                          precision={UNIT_META[d.unit].decimal ? 2 : 0}
                          inputMode={UNIT_META[d.unit].decimal ? 'decimal' : 'numeric'}
                          style={{ width: '100%' }}
                        />
                      </Form.Item>
                    ),
                  },
                  { title: 'Chênh lệch', key: 'diff', align: 'right', render: (_: unknown, d, i) => diffCell(d, i) },
                ]}
              />
            </Form.Item>
          )}

          <Form.Item
            label={hasDeclared ? 'Món khách không khai (nếu có)' : 'Đếm đồ theo từng loại'}
            required={!hasDeclared}
            extra={hasDeclared ? undefined : 'Chăn ga, đồng phục tính theo kg thì nhập số cân (được lẻ, VD 12,5).'}
            className="mb-4"
          >
            <CatalogLines name="items" catalog={catalog} loading={loadingCatalog} minLines={hasDeclared ? 0 : 1} addLabel={hasDeclared ? 'Thêm món khách không khai' : 'Thêm loại đồ'} />
          </Form.Item>

          <Form.Item name="totalBags" label="Số túi sau khi gom" rules={[{ required: true, message: 'Vui lòng nhập số túi' }]}>
            <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} inputMode="numeric" />
          </Form.Item>
          <Form.Item name="description" label="Ghi chú" rules={[{ max: 1000, message: 'Tối đa 1000 ký tự' }]}>
            <Input.TextArea rows={2} maxLength={1000} placeholder="Ví dụ: 2 ga giường bị ố vàng, áo đầu bếp dính dầu..." />
          </Form.Item>

          <div className="proof-box">
            <Typography.Text strong>{atWorkshop ? 'Ảnh túi đồ (không bắt buộc)' : 'Ảnh túi đồ (bắt buộc)'}</Typography.Text>
            <PhotoPicker value={photos} onChange={setPhotos} label="Chụp ảnh túi đồ" />
            <Collapse
              size="small"
              items={[
                {
                  key: 'sign',
                  label: `Khách ký (không bắt buộc)${signature ? ' · đã ký' : ''}`,
                  children: <SignaturePad onChange={setSignature} label="Khách ký xác nhận đã giao đồ" />,
                },
              ]}
            />
          </div>
          <Typography.Text type="secondary">Số thực nhận là số chốt để giặt và tính tiền. Có chênh lệch, hệ thống tự báo cho khách.</Typography.Text>
        </Form>
      )}
    </Modal>
  )
}
