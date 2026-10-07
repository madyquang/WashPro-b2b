import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Alert, Button, Card, Col, Empty, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Skeleton, Space, Tabs, Tag, Typography, message } from 'antd'
import { CarOutlined, EnvironmentOutlined, PhoneOutlined, PlusOutlined, ReloadOutlined, UserOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import CreateOrderModal from './orders/CreateOrderModal'
import { getSession, isRestaurantRole } from '../utils/auth'
import { formatDate, getErrorMessage, todayIso } from '../utils/format'
import {
  CUSTOMER_TYPE_LABELS,
  PICKUP_STATUS_META,
  TIME_SLOT_LABELS,
  type PickupRequest,
  type Restaurant,
  type TimeSlot,
  type UserAccount,
} from '../types'

type TabKey = 'active' | 'mine' | 'open' | 'done'

interface RequestForm {
  restaurantId?: number
  preferredDate: string
  timeSlot: TimeSlot
  estimatedBags?: number
  note?: string
}

const TIME_SLOT_OPTIONS = (Object.keys(TIME_SLOT_LABELS) as TimeSlot[]).map((t) => ({ value: t, label: TIME_SLOT_LABELS[t] }))

const mapsUrl = (address: string) => `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`

/** Yêu cầu lấy đồ: khách tạo, tài xế nhận việc rồi tạo đơn, quản lý xưởng điều phối. */
export default function Pickups() {
  const session = getSession()
  const role = session?.role
  const isCustomer = role ? isRestaurantRole(role) : false
  const isDriver = role === 'DRIVER'
  const isAdmin = role === 'WORKSHOP_ADMIN'

  const [rows, setRows] = useState<PickupRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<TabKey>(isDriver ? 'mine' : 'active')
  const [busyId, setBusyId] = useState<number | null>(null)

  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<RequestForm>()
  const [customers, setCustomers] = useState<Restaurant[]>([])
  const [drivers, setDrivers] = useState<UserAccount[]>([])
  const [orderFrom, setOrderFrom] = useState<PickupRequest | null>(null)
  const [cancelTarget, setCancelTarget] = useState<PickupRequest | null>(null)
  const [cancelReason, setCancelReason] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.get<PickupRequest[]>('/pickups')
      setRows(data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được yêu cầu lấy đồ'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    if (!isAdmin) return
    Promise.all([axiosClient.get<Restaurant[]>('/restaurants'), axiosClient.get<UserAccount[]>('/users', { params: { role: 'DRIVER' } })])
      .then(([c, d]) => {
        setCustomers(c.data.filter((r) => r.active))
        setDrivers(d.data.filter((u) => u.enabled))
      })
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được danh sách khách hàng / tài xế')))
  }, [isAdmin])

  const visible = useMemo(() => {
    const active = (r: PickupRequest) => r.status === 'OPEN' || r.status === 'ACCEPTED'
    const sorted = [...rows].sort((a, b) => a.preferredDate.localeCompare(b.preferredDate) || a.id - b.id)
    switch (tab) {
      case 'mine':
        return sorted.filter((r) => r.status === 'ACCEPTED')
      case 'open':
        return sorted.filter((r) => r.status === 'OPEN')
      case 'done':
        return [...rows].filter((r) => !active(r))
      default:
        return sorted.filter(active)
    }
  }, [rows, tab])

  if (!role) return null

  const run = async (id: number, request: () => Promise<unknown>, success: string) => {
    setBusyId(id)
    try {
      await request()
      message.success(success)
      await load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ preferredDate: todayIso(), timeSlot: 'ANYTIME' })
    setCreating(true)
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
      load()
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

  const tabs = isDriver
    ? [
        { key: 'mine', label: `Việc của tôi (${rows.filter((r) => r.status === 'ACCEPTED').length})` },
        { key: 'open', label: `Chờ nhận (${rows.filter((r) => r.status === 'OPEN').length})` },
        { key: 'done', label: 'Đã xong' },
      ]
    : [
        { key: 'active', label: `Đang chờ (${rows.filter((r) => r.status === 'OPEN' || r.status === 'ACCEPTED').length})` },
        { key: 'done', label: 'Đã xong / đã hủy' },
      ]

  const renderCard = (r: PickupRequest) => {
    const busy = busyId === r.id
    const active = r.status === 'OPEN' || r.status === 'ACCEPTED'
    const today = r.preferredDate === todayIso()
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
      actions.push(
        <Button key="order" type="primary" block icon={<PlusOutlined />} onClick={() => setOrderFrom(r)}>
          Đã đến · Tạo đơn
        </Button>,
        <Popconfirm key="release" title="Trả lại việc này cho tài xế khác?" okText="Trả lại" cancelText="Đóng" onConfirm={() => run(r.id, () => axiosClient.put(`/pickups/${r.id}/release`), 'Đã trả lại việc')}>
          <Button block loading={busy}>
            Trả lại
          </Button>
        </Popconfirm>,
      )
    }
    if (isAdmin && active) {
      actions.push(
        <Select
          key="assign"
          placeholder="Phân công tài xế"
          style={{ width: '100%' }}
          value={r.driverId ?? undefined}
          loading={busy}
          options={[
            ...drivers.map((d) => ({ value: d.id, label: d.fullName || d.username })),
            // Tài xế đang nhận có thể đã bị khóa (không còn trong danh sách): vẫn hiện đúng tên
            ...(r.driverId && !drivers.some((d) => d.id === r.driverId) ? [{ value: r.driverId, label: r.driverName ?? `#${r.driverId}` }] : []),
          ]}
          onChange={(driverId: number) => run(r.id, () => axiosClient.put(`/pickups/${r.id}/assign`, { driverId }), 'Đã phân công tài xế')}
        />,
      )
    }
    if ((isCustomer || isAdmin) && active) {
      actions.push(
        <Button key="cancel" danger type="text" block onClick={() => setCancelTarget(r)}>
          Hủy yêu cầu
        </Button>,
      )
    }

    return (
      <Card
        size="small"
        title={
          <Space size={4} wrap>
            <span>#{r.id}</span>
            {!isCustomer && <span>{r.restaurantName}</span>}
          </Space>
        }
        extra={<Tag color={PICKUP_STATUS_META[r.status].color}>{PICKUP_STATUS_META[r.status].label}</Tag>}
        style={{ height: '100%', borderColor: late ? '#ff7875' : undefined }}
      >
        <Space direction="vertical" size={6} style={{ width: '100%' }}>
          <div>
            <CarOutlined />{' '}
            <Typography.Text strong type={late ? 'danger' : undefined}>
              {today ? 'Hôm nay' : formatDate(r.preferredDate)}
            </Typography.Text>{' '}
            · {TIME_SLOT_LABELS[r.timeSlot]}
            {late && <Tag color="red" style={{ marginInlineStart: 6 }}>Quá ngày hẹn</Tag>}
          </div>
          {!isCustomer && (
            <>
              <Typography.Text type="secondary">{CUSTOMER_TYPE_LABELS[r.customerType]}</Typography.Text>
              {r.address && (
                <a href={mapsUrl(r.address)} target="_blank" rel="noreferrer">
                  <EnvironmentOutlined /> {r.address}
                </a>
              )}
              {r.phone && (
                <a href={`tel:${r.phone}`}>
                  <PhoneOutlined /> {r.phone}
                  {r.contactName && ` · ${r.contactName}`}
                </a>
              )}
            </>
          )}
          {r.estimatedBags && <div>Khoảng {r.estimatedBags} túi</div>}
          {r.note && <Typography.Paragraph style={{ margin: 0 }} className="pre-wrap">“{r.note}”</Typography.Paragraph>}
          {r.driverName && (
            <div>
              <UserOutlined /> Tài xế: {r.driverName}
            </div>
          )}
          {r.orderId && <Typography.Text type="success">Đã tạo đơn #{r.orderId}</Typography.Text>}
          {r.status === 'CANCELLED' && (
            <Typography.Text type="secondary">
              Hủy bởi {r.cancelledBy}
              {r.cancelReason && `: ${r.cancelReason}`}
            </Typography.Text>
          )}
          {actions.length > 0 && <Space direction="vertical" style={{ width: '100%', marginTop: 4 }}>{actions}</Space>}
        </Space>
      </Card>
    )
  }

  return (
    <>
      <div className="page-toolbar">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Lấy đồ
          </Typography.Title>
          <Typography.Text type="secondary">
            {isCustomer && 'Gửi yêu cầu khi cần xưởng đến lấy đồ bẩn.'}
            {isDriver && 'Nhận việc, đến lấy đồ rồi tạo đơn ngay tại chỗ.'}
            {isAdmin && 'Theo dõi và phân công tài xế cho các yêu cầu lấy đồ.'}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
            Làm mới
          </Button>
          {(isCustomer || isAdmin) && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              {isAdmin ? 'Tạo yêu cầu hộ khách' : 'Yêu cầu lấy đồ'}
            </Button>
          )}
        </Space>
      </div>

      {error && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={error} action={<Button size="small" onClick={load}>Thử lại</Button>} />}

      <Tabs activeKey={tab} onChange={(k) => setTab(k as TabKey)} items={tabs} />

      {loading && rows.length === 0 ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : visible.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={tab === 'mine' ? 'Bạn chưa nhận việc nào. Xem tab "Chờ nhận".' : 'Không có yêu cầu nào'}
        />
      ) : (
        <Row gutter={[12, 12]}>
          {visible.map((r) => (
            <Col key={r.id} xs={24} sm={12} xl={8}>
              {renderCard(r)}
            </Col>
          ))}
        </Row>
      )}

      <Modal
        title={isAdmin ? 'Tạo yêu cầu lấy đồ hộ khách' : 'Yêu cầu xưởng đến lấy đồ'}
        open={creating}
        onCancel={() => setCreating(false)}
        onOk={() => form.submit()}
        okText="Gửi yêu cầu"
        cancelText="Hủy"
        confirmLoading={saving}
        forceRender
      >
        <Form<RequestForm> form={form} layout="vertical" onFinish={submit}>
          {isAdmin && (
            <Form.Item name="restaurantId" label="Khách hàng" rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}>
              <Select showSearch={{ optionFilterProp: 'label' }} options={customers.map((c) => ({ value: c.id, label: c.name }))} />
            </Form.Item>
          )}
          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item name="preferredDate" label="Ngày lấy" rules={[{ required: true, message: 'Vui lòng chọn ngày' }]}>
                <Input type="date" min={todayIso()} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="timeSlot" label="Khung giờ">
                <Select options={TIME_SLOT_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
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
        <Input.TextArea rows={3} maxLength={500} placeholder="Lý do (không bắt buộc)" value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} />
      </Modal>

      <CreateOrderModal
        open={orderFrom !== null}
        fromPickup={orderFrom}
        onClose={() => setOrderFrom(null)}
        onDone={() => {
          setOrderFrom(null)
          load()
        }}
      />
    </>
  )
}
