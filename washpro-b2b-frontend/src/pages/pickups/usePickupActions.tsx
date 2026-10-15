import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Radio, Select, Typography } from 'antd'
import { message } from '../../utils/message'
import { CalendarOutlined, CarOutlined, EnvironmentOutlined, PhoneOutlined, PlusOutlined, UserOutlined, WarningOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import PickupStatusTag from '../../components/PickupStatusTag'
import CreateOrderModal from '../orders/CreateOrderModal'
import { getSession, isRestaurantRole } from '../../utils/auth'
import { formatDate, getErrorMessage, todayIso } from '../../utils/format'
import { CUSTOMER_TYPE_LABELS, TIME_SLOT_LABELS, type PickupRequest, type Restaurant, type TimeSlot, type UserAccount } from '../../types'

interface RequestForm {
  restaurantId?: number
  preferredDate: string
  timeSlot: TimeSlot
  estimatedBags?: number
  note?: string
}

const TIME_SLOT_OPTIONS = (Object.keys(TIME_SLOT_LABELS) as TimeSlot[]).map((t) => ({ value: t, label: TIME_SLOT_LABELS[t] }))

const mapsUrl = (address: string) => `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`

export const isActivePickup = (r: PickupRequest) => r.status === 'OPEN' || r.status === 'ACCEPTED'

/** "Hôm nay" / "Ngày mai" / ngày cụ thể. */
const whenLabel = (date: string) => {
  const today = todayIso()
  if (date === today) return 'Hôm nay'
  const t = new Date(`${today}T00:00:00`)
  t.setDate(t.getDate() + 1)
  const tomorrow = `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
  return date === tomorrow ? 'Ngày mai' : formatDate(date)
}

/**
 * Thao tác trên yêu cầu lấy đồ theo vai trò + các hộp thoại đi kèm (gửi yêu cầu, hủy, tạo đơn từ yêu cầu).
 * Dùng chung cho trang Lấy đồ và trang chủ (khách gửi yêu cầu, tài xế nhận việc ngay tại trang chủ).
 */
export function usePickupActions(onChanged: () => void) {
  const role = getSession()?.role
  const isCustomer = role ? isRestaurantRole(role) : false
  const isDriver = role === 'DRIVER'
  const isAdmin = role === 'WORKSHOP_ADMIN'
  const canCreate = isCustomer || isAdmin

  const [busyId, setBusyId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<RequestForm>()
  const [customers, setCustomers] = useState<Restaurant[]>([])
  const [drivers, setDrivers] = useState<UserAccount[]>([])
  const [orderFrom, setOrderFrom] = useState<PickupRequest | null>(null)
  const [cancelTarget, setCancelTarget] = useState<PickupRequest | null>(null)
  const [cancelReason, setCancelReason] = useState('')

  // Quản lý xưởng cần danh sách khách (tạo yêu cầu hộ) và tài xế (phân công)
  useEffect(() => {
    if (!isAdmin) return
    Promise.all([axiosClient.get<Restaurant[]>('/restaurants'), axiosClient.get<UserAccount[]>('/users', { params: { role: 'DRIVER' } })])
      .then(([c, d]) => {
        setCustomers(c.data.filter((r) => r.active))
        setDrivers(d.data.filter((u) => u.enabled))
      })
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được danh sách khách hàng / tài xế')))
  }, [isAdmin])

  const openCreate = useCallback(() => {
    form.resetFields()
    form.setFieldsValue({ preferredDate: todayIso(), timeSlot: 'ANYTIME' })
    setCreating(true)
  }, [form])

  const run = async (id: number, request: () => Promise<unknown>, success: string) => {
    setBusyId(id)
    try {
      await request()
      message.success(success)
      onChanged()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  const submit = async (values: RequestForm) => {
    setSaving(true)
    try {
      await axiosClient.post('/pickups', {
        restaurantId: values.restaurantId ?? null,
        preferredDate: values.preferredDate,
        timeSlot: values.timeSlot,
        estimatedBags: values.estimatedBags ?? null,
        note: values.note?.trim() || null,
      })
      message.success('Đã gửi yêu cầu, xưởng sẽ cử tài xế đến lấy đồ')
      setCreating(false)
      onChanged()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const confirmCancel = async () => {
    if (!cancelTarget) return
    const target = cancelTarget
    setCancelTarget(null)
    await run(target.id, () => axiosClient.put(`/pickups/${target.id}/cancel`, { reason: cancelReason.trim() || null }), 'Đã hủy yêu cầu')
    setCancelReason('')
  }

  /** Câu mô tả tình trạng cho khách: họ quan tâm "khi nào ai đến", không phải mã trạng thái. */
  const customerStatusLine = (r: PickupRequest) => {
    if (r.status === 'OPEN') return 'Xưởng đã nhận yêu cầu, đang sắp xếp tài xế.'
    if (r.status === 'ACCEPTED') return `Tài xế ${r.driverName ?? ''} sẽ đến lấy đồ.`
    if (r.status === 'DONE') return r.orderId ? `Đã lấy đồ, tạo đơn #${r.orderId}.` : 'Đã lấy đồ.'
    return undefined
  }

  /** Thẻ một yêu cầu lấy đồ, nội dung và nút theo vai trò. */
  const cardFor = (r: PickupRequest): ReactNode => {
    const busy = busyId === r.id
    const active = isActivePickup(r)
    const late = active && r.preferredDate < todayIso()
    const actions: ReactNode[] = []

    if (isDriver && r.status === 'OPEN') {
      actions.push(
        <Button key="accept" type="primary" block loading={busy} onClick={() => run(r.id, () => axiosClient.put(`/pickups/${r.id}/accept`), 'Đã nhận việc')}>
          Nhận việc
        </Button>,
      )
    }
    if (isDriver && r.status === 'ACCEPTED') {
      // Tài xế đứng ngoài đường: gọi và chỉ đường là hai việc hay làm nhất, đặt thành nút lớn
      const quick: ReactNode[] = []
      if (r.phone)
        quick.push(
          <Button key="call" icon={<PhoneOutlined />} href={`tel:${r.phone}`}>
            Gọi
          </Button>,
        )
      if (r.address)
        quick.push(
          <Button key="map" icon={<EnvironmentOutlined />} href={mapsUrl(r.address)} target="_blank" rel="noreferrer">
            Chỉ đường
          </Button>,
        )
      if (quick.length)
        actions.push(
          <div key="quick" className="pickup-quick">
            {quick}
          </div>,
        )
      actions.push(
        <Button key="order" type="primary" block icon={<PlusOutlined />} onClick={() => setOrderFrom(r)}>
          Đã đến · Tạo đơn
        </Button>,
        <Popconfirm key="release" title="Trả lại việc này cho tài xế khác?" okText="Trả lại" cancelText="Đóng" onConfirm={() => run(r.id, () => axiosClient.put(`/pickups/${r.id}/release`), 'Đã trả lại việc')}>
          <Button block type="text" loading={busy}>
            Trả lại việc
          </Button>
        </Popconfirm>,
      )
    }
    if (isAdmin && active) {
      actions.push(
        <label key="assign" className="field-label">
          <span>{r.driverId ? 'Đổi tài xế' : 'Phân công tài xế'}</span>
          <Select
            aria-label={`Phân công tài xế cho yêu cầu #${r.id}`}
            placeholder="Chọn tài xế"
            style={{ width: '100%' }}
            value={r.driverId ?? undefined}
            loading={busy}
            options={[
              ...drivers.map((d) => ({ value: d.id, label: d.fullName || d.username })),
              // Tài xế đang nhận có thể đã bị khóa (không còn trong danh sách): vẫn hiện đúng tên
              ...(r.driverId && !drivers.some((d) => d.id === r.driverId) ? [{ value: r.driverId, label: r.driverName ?? `#${r.driverId}` }] : []),
            ]}
            onChange={(driverId: number) => run(r.id, () => axiosClient.put(`/pickups/${r.id}/assign`, { driverId }), 'Đã phân công tài xế')}
          />
        </label>,
      )
    }
    if ((isCustomer || isAdmin) && active) {
      actions.push(
        <Button key="cancel" danger type="text" block onClick={() => setCancelTarget(r)}>
          Hủy yêu cầu
        </Button>,
      )
    }

    const statusLine = isCustomer ? customerStatusLine(r) : undefined
    return (
      <Card
        key={r.id}
        size="small"
        className={late ? 'is-flagged' : undefined}
        title={isCustomer ? `Yêu cầu #${r.id}` : `#${r.id} · ${r.restaurantName}`}
        extra={<PickupStatusTag status={r.status} />}
        style={{ height: '100%' }}
      >
        <div className="pickup-body">
          <div className="pickup-when">
            <CalendarOutlined aria-hidden />
            <Typography.Text strong type={late ? 'danger' : undefined}>
              {whenLabel(r.preferredDate)}
            </Typography.Text>
            <span>· {TIME_SLOT_LABELS[r.timeSlot]}</span>
            {late && (
              <Typography.Text type="danger" strong>
                <WarningOutlined aria-hidden /> Quá ngày hẹn
              </Typography.Text>
            )}
          </div>
          {statusLine && <div className="order-card-hint">{statusLine}</div>}
          {!isCustomer && (
            <>
              <Typography.Text type="secondary">{CUSTOMER_TYPE_LABELS[r.customerType]}</Typography.Text>
              {r.address && (
                <span>
                  <EnvironmentOutlined aria-hidden /> {r.address}
                </span>
              )}
              {r.phone && (
                <span>
                  <PhoneOutlined aria-hidden /> {r.phone}
                  {r.contactName && ` · ${r.contactName}`}
                </span>
              )}
            </>
          )}
          {r.estimatedBags && (
            <span>
              <CarOutlined aria-hidden /> Khoảng {r.estimatedBags} túi
            </span>
          )}
          {r.note && <div className="pickup-note pre-wrap">“{r.note}”</div>}
          {/* Tài xế không cần thấy tên chính mình; khách đã có câu mô tả ở trên */}
          {r.driverName && !isCustomer && !isDriver && (
            <span>
              <UserOutlined aria-hidden /> Tài xế: {r.driverName}
            </span>
          )}
          {r.orderId && !isCustomer && <Typography.Text type="success">Đã tạo đơn #{r.orderId}</Typography.Text>}
          {r.status === 'CANCELLED' && (
            <Typography.Text type="secondary">
              Hủy bởi {r.cancelledBy}
              {r.cancelReason && `: ${r.cancelReason}`}
            </Typography.Text>
          )}
          {actions.length > 0 && <div className="pickup-actions">{actions}</div>}
        </div>
      </Card>
    )
  }

  const ui = (
    <>
      <Modal
        title={isAdmin ? 'Tạo yêu cầu lấy đồ hộ khách' : 'Yêu cầu xưởng đến lấy đồ'}
        open={creating}
        onCancel={() => setCreating(false)}
        onOk={() => form.submit()}
        okText="Gửi yêu cầu"
        cancelText="Hủy"
        confirmLoading={saving}
        forceRender
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
      >
        <Form<RequestForm> form={form} layout="vertical" onFinish={submit}>
          {isAdmin && (
            <Form.Item name="restaurantId" label="Khách hàng" rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}>
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

      <Modal
        title={cancelTarget ? `Hủy yêu cầu lấy đồ #${cancelTarget.id}?` : ''}
        open={cancelTarget !== null}
        onCancel={() => setCancelTarget(null)}
        onOk={confirmCancel}
        okText="Hủy yêu cầu"
        cancelText="Đóng"
        okButtonProps={{ danger: true }}
        destroyOnHidden
      >
        <Input.TextArea rows={3} maxLength={500} aria-label="Lý do hủy" placeholder="Lý do (không bắt buộc)" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} />
      </Modal>

      <CreateOrderModal
        open={orderFrom !== null}
        fromPickup={orderFrom}
        onClose={() => setOrderFrom(null)}
        onDone={() => {
          setOrderFrom(null)
          onChanged()
        }}
      />
    </>
  )

  return { cardFor, openCreate, canCreate, ui }
}
