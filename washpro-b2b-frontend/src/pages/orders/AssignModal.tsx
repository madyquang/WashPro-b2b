import { useEffect, useState } from 'react'
import { Modal, Select, Typography } from 'antd'
import axiosClient from '../../api/axiosClient'
import { getErrorMessage } from '../../utils/format'
import { message } from '../../utils/message'
import { isPickupStage, type Order, type UserAccount } from '../../types'

/** Quản lý xưởng phân công (hoặc đổi) tài xế lấy đồ / giao trả. */
export default function AssignModal({ order, onClose, onDone }: { order: Order | null; onClose: () => void; onDone: () => void }) {
  const [drivers, setDrivers] = useState<UserAccount[]>([])
  const [driverId, setDriverId] = useState<number | undefined>()
  const [saving, setSaving] = useState(false)
  const pickup = order ? isPickupStage(order) : true
  const current = order ? (pickup ? order.driverId : order.deliveryDriverId) : null

  useEffect(() => {
    if (!order) return
    setDriverId(undefined)
    axiosClient
      .get<UserAccount[]>('/users', { params: { role: 'DRIVER' } })
      .then(({ data }) => setDrivers(data.filter((u) => u.enabled)))
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được danh sách tài xế')))
  }, [order])

  const save = async () => {
    if (!order || !driverId) return
    setSaving(true)
    try {
      await axiosClient.put(`/orders/${order.id}/assign`, { driverId })
      message.success('Đã phân công tài xế')
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={order ? `${pickup ? 'Phân công tài xế lấy đồ' : 'Phân công tài xế giao trả'} · đơn #${order.id}` : ''}
      open={order !== null}
      onCancel={onClose}
      onOk={save}
      okText="Phân công"
      cancelText="Đóng"
      okButtonProps={{ disabled: !driverId || driverId === current }}
      confirmLoading={saving}
      destroyOnHidden
    >
      {order && (
        <Typography.Paragraph type="secondary">
          {order.customerName}
          {order.customerAddress && ` · ${order.customerAddress}`}
        </Typography.Paragraph>
      )}
      <Select
        aria-label="Tài xế"
        placeholder="Chọn tài xế"
        showSearch={{ optionFilterProp: 'label' }}
        style={{ width: '100%' }}
        value={driverId ?? current ?? undefined}
        onChange={setDriverId}
        options={[
          ...drivers.map((d) => ({ value: d.id, label: d.fullName || d.username })),
          // Tài xế đang nhận có thể đã bị khóa (không còn trong danh sách): vẫn hiện đúng tên
          ...(order && current && !drivers.some((d) => d.id === current)
            ? [{ value: current, label: (pickup ? order.driverName : order.deliveryDriverName) ?? `#${current}` }]
            : []),
        ]}
      />
    </Modal>
  )
}
