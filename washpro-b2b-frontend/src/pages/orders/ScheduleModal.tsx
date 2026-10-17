import { useState } from 'react'
import { Form, Input, Modal, Radio, Typography } from 'antd'
import axiosClient from '../../api/axiosClient'
import { getErrorMessage, todayIso } from '../../utils/format'
import { message } from '../../utils/message'
import { TIME_SLOT_OPTIONS } from './RequestModal'
import type { Order, TimeSlot } from '../../types'

interface ScheduleForm {
  preferredDate: string
  timeSlot: TimeSlot
}

/** Đổi ngày / khung giờ lấy đồ khi tài xế chưa lấy (thay vì hủy rồi gửi yêu cầu mới). */
export default function ScheduleModal({ order, onClose, onDone }: { order: Order | null; onClose: () => void; onDone: () => void }) {
  const [form] = Form.useForm<ScheduleForm>()
  const [saving, setSaving] = useState(false)

  const submit = async (values: ScheduleForm) => {
    if (!order) return
    setSaving(true)
    try {
      await axiosClient.put(`/orders/${order.id}/schedule`, values)
      message.success('Đã đổi ngày giờ lấy đồ')
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={order ? `Đổi ngày giờ lấy đồ · yêu cầu #${order.id}` : ''}
      open={order !== null}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Lưu lịch mới"
      cancelText="Đóng"
      confirmLoading={saving}
      className="sheet-modal"
      width={{ xs: '100%', md: 520 }}
      destroyOnHidden
    >
      {order && (
        <Form<ScheduleForm>
          form={form}
          name="pickup-schedule"
          layout="vertical"
          onFinish={submit}
          initialValues={{ preferredDate: order.preferredDate && order.preferredDate >= todayIso() ? order.preferredDate : todayIso(), timeSlot: order.timeSlot ?? 'ANYTIME' }}
        >
          {order.driverName && (
            <Typography.Paragraph type="secondary">Tài xế {order.driverName} đã nhận việc và sẽ được báo lịch mới.</Typography.Paragraph>
          )}
          <Form.Item name="preferredDate" label="Ngày lấy" rules={[{ required: true, message: 'Vui lòng chọn ngày' }]}>
            <Input type="date" min={todayIso()} />
          </Form.Item>
          <Form.Item name="timeSlot" label="Khung giờ">
            <Radio.Group optionType="button" buttonStyle="solid" className="choice-grid" options={TIME_SLOT_OPTIONS} />
          </Form.Item>
        </Form>
      )}
    </Modal>
  )
}
