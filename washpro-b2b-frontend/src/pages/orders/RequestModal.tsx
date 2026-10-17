import { useEffect, useState } from 'react'
import { Form, Input, InputNumber, Modal, Radio, Select } from 'antd'
import axiosClient from '../../api/axiosClient'
import { getErrorMessage, todayIso } from '../../utils/format'
import { message } from '../../utils/message'
import { TIME_SLOT_LABELS, type Customer, type TimeSlot } from '../../types'

interface RequestForm {
  customerId?: number
  preferredDate: string
  timeSlot: TimeSlot
  estimatedBags?: number
  note?: string
}

export const TIME_SLOT_OPTIONS = (Object.keys(TIME_SLOT_LABELS) as TimeSlot[]).map((t) => ({ value: t, label: TIME_SLOT_LABELS[t] }))

/** Khách yêu cầu xưởng đến lấy đồ; quản lý xưởng tạo hộ khi khách gọi điện (chọn khách hàng). */
export default function RequestModal({ open, forWorkshop, onClose, onDone }: { open: boolean; forWorkshop: boolean; onClose: () => void; onDone: () => void }) {
  const [form] = Form.useForm<RequestForm>()
  const [customers, setCustomers] = useState<Customer[]>([])
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue({ preferredDate: todayIso(), timeSlot: 'ANYTIME' })
    if (!forWorkshop) return
    axiosClient
      .get<Customer[]>('/customers')
      .then(({ data }) => setCustomers(data.filter((c) => c.active)))
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được danh sách khách hàng')))
  }, [open, forWorkshop, form])

  const submit = async (values: RequestForm) => {
    setSaving(true)
    try {
      await axiosClient.post('/orders', {
        customerId: values.customerId ?? null,
        preferredDate: values.preferredDate,
        timeSlot: values.timeSlot,
        estimatedBags: values.estimatedBags ?? null,
        note: values.note?.trim() || null,
      })
      message.success('Đã gửi yêu cầu, xưởng sẽ cử tài xế đến lấy đồ')
      onDone()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={forWorkshop ? 'Tạo yêu cầu lấy đồ hộ khách' : 'Yêu cầu xưởng đến lấy đồ'}
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Gửi yêu cầu"
      cancelText="Hủy"
      confirmLoading={saving}
      forceRender
      className="sheet-modal"
      width={{ xs: '100%', md: 520 }}
    >
      <Form<RequestForm> form={form} name="pickup-request" layout="vertical" onFinish={submit}>
        {forWorkshop && (
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}>
            <Select showSearch={{ optionFilterProp: 'label' }} options={customers.map((c) => ({ value: c.id, label: c.name }))} />
          </Form.Item>
        )}
        <Form.Item name="preferredDate" label="Ngày lấy" rules={[{ required: true, message: 'Vui lòng chọn ngày' }]}>
          <Input type="date" min={todayIso()} />
        </Form.Item>
        {/* Khung giờ chiếm trọn hàng để nhãn "Giờ nào cũng được" không bị cắt */}
        <Form.Item name="timeSlot" label="Khung giờ">
          <Radio.Group optionType="button" buttonStyle="solid" className="choice-grid" options={TIME_SLOT_OPTIONS} />
        </Form.Item>
        <Form.Item name="estimatedBags" label="Số túi ước tính (không bắt buộc)">
          <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} inputMode="numeric" />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú cho tài xế">
          <Input.TextArea rows={3} maxLength={500} showCount placeholder="Ví dụ: lấy ở cửa sau, gọi trước 15 phút, có 2 bao chăn ga lớn..." />
        </Form.Item>
      </Form>
    </Modal>
  )
}
