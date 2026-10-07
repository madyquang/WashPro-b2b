import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { PlusOutlined, ReloadOutlined, UserAddOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import { formatDate, getErrorMessage } from '../utils/format'
import {
  CUSTOMER_TYPE_LABELS,
  PAYMENT_TERM_OPTIONS,
  TURNAROUND_OPTIONS,
  paymentTermLabel,
  type CustomerType,
  type Restaurant,
} from '../types'

const CUSTOMER_TYPE_OPTIONS = (Object.keys(CUSTOMER_TYPE_LABELS) as CustomerType[]).map((t) => ({ value: t, label: CUSTOMER_TYPE_LABELS[t] }))

interface RestaurantForm {
  name: string
  customerType: CustomerType
  address: string
  phone?: string
  contactName?: string
  turnaroundHours: number
  paymentTermDays: number
}

interface AccountForm {
  username: string
  password: string
  fullName?: string
  role: 'RESTAURANT_MANAGER' | 'RESTAURANT_STAFF'
}

const PHONE_RULE = { pattern: /^$|^[0-9+()\-. ]{8,20}$/, message: 'Số điện thoại không hợp lệ' }

export default function Restaurants() {
  const [rows, setRows] = useState<Restaurant[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  const [editing, setEditing] = useState<Restaurant | 'new' | null>(null)
  const [accountFor, setAccountFor] = useState<Restaurant | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<RestaurantForm>()
  const [accountForm] = Form.useForm<AccountForm>()

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.get<Restaurant[]>('/restaurants')
      setRows(data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được danh sách khách hàng'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const visible = useMemo(() => {
    const k = search.trim().toLowerCase()
    return rows.filter((r) => !k || r.name.toLowerCase().includes(k) || r.address?.toLowerCase().includes(k))
  }, [rows, search])

  const openEditor = (target: Restaurant | 'new') => {
    setEditing(target)
    form.setFieldsValue(
      target === 'new'
        ? { name: '', customerType: 'RESTAURANT', address: '', phone: '', contactName: '', turnaroundHours: 48, paymentTermDays: 0 }
        : {
            name: target.name,
            customerType: target.customerType,
            address: target.address,
            phone: target.phone ?? '',
            contactName: target.contactName ?? '',
            turnaroundHours: target.turnaroundHours,
            paymentTermDays: target.paymentTermDays,
          },
    )
  }

  const saveRestaurant = async (values: RestaurantForm) => {
    setSaving(true)
    try {
      const body = {
        name: values.name.trim(),
        customerType: values.customerType,
        address: values.address.trim(),
        phone: values.phone?.trim() ?? '',
        contactName: values.contactName?.trim() || null,
        turnaroundHours: values.turnaroundHours,
        paymentTermDays: values.paymentTermDays,
      }
      if (editing === 'new') {
        await axiosClient.post('/restaurants', body)
        message.success('Đã thêm khách hàng')
      } else if (editing) {
        await axiosClient.put(`/restaurants/${editing.id}`, body)
        message.success('Đã cập nhật khách hàng')
      }
      setEditing(null)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const toggleActive = async (r: Restaurant) => {
    try {
      await axiosClient.patch(`/restaurants/${r.id}/active`, { enabled: !r.active })
      message.success(r.active ? 'Đã ngừng hợp tác' : 'Đã kích hoạt lại')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const createAccount = async (values: AccountForm) => {
    if (!accountFor) return
    setSaving(true)
    try {
      await axiosClient.post('/users', {
        username: values.username.trim(),
        password: values.password,
        fullName: values.fullName?.trim() || null,
        role: values.role,
        restaurantId: accountFor.id,
      })
      message.success(`Đã cấp tài khoản cho ${accountFor.name}`)
      setAccountFor(null)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const columns: TableColumnsType<Restaurant> = [
    { title: 'Tên khách hàng', dataIndex: 'name', sorter: (a, b) => a.name.localeCompare(b.name, 'vi') },
    {
      title: 'Loại khách',
      dataIndex: 'customerType',
      filters: CUSTOMER_TYPE_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
      onFilter: (value, r) => r.customerType === value,
      render: (t: CustomerType) => CUSTOMER_TYPE_LABELS[t],
    },
    { title: 'Địa chỉ', dataIndex: 'address', responsive: ['md'] },
    { title: 'Điện thoại', dataIndex: 'phone', responsive: ['md'], render: (v: string | null) => v || '—' },
    { title: 'Trả đồ', dataIndex: 'turnaroundHours', responsive: ['lg'], render: (h: number) => `${h} giờ` },
    { title: 'Thanh toán', dataIndex: 'paymentTermDays', responsive: ['lg'], render: (d: number) => paymentTermLabel(d) },
    { title: 'Ngày thêm', dataIndex: 'createdAt', responsive: ['xl'], render: (v: string) => formatDate(v) },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 130,
      render: (a: boolean) => (a ? <Tag color="green">Đang hợp tác</Tag> : <Tag>Ngừng hợp tác</Tag>),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      render: (_: unknown, r) => (
        <Space size={4} wrap>
          <Button size="small" onClick={() => openEditor(r)}>
            Sửa
          </Button>
          {r.active && (
            <Button
              size="small"
              icon={<UserAddOutlined />}
              onClick={() => {
                accountForm.resetFields()
                accountForm.setFieldsValue({ role: 'RESTAURANT_MANAGER' })
                setAccountFor(r)
              }}
            >
              Cấp tài khoản
            </Button>
          )}
          <Popconfirm
            title={r.active ? 'Ngừng hợp tác với khách hàng này?' : 'Kích hoạt lại khách hàng này?'}
            description={r.active ? 'Không thể tạo đơn mới, lịch sử vẫn được giữ nguyên.' : undefined}
            okText="Đồng ý"
            cancelText="Đóng"
            onConfirm={() => toggleActive(r)}
          >
            <Button size="small" danger={r.active} type="text">
              {r.active ? 'Ngừng hợp tác' : 'Kích hoạt'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <div className="page-toolbar">
        <Typography.Title level={3} style={{ margin: 0 }}>
          Khách hàng
        </Typography.Title>
        <Space wrap>
          <Input.Search allowClear placeholder="Tìm theo tên, địa chỉ" style={{ width: 240 }} onChange={(e) => setSearch(e.target.value)} />
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
            Làm mới
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('new')}>
            Thêm khách hàng
          </Button>
        </Space>
      </div>

      {error && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={error} action={<Button size="small" onClick={load}>Thử lại</Button>} />}

      <Table<Restaurant>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={visible}
        scroll={{ x: 'max-content' }}
        pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (t) => `${t} khách hàng` }}
        locale={{ emptyText: 'Chưa có khách hàng nào' }}
      />

      <Modal
        title={editing === 'new' ? 'Thêm khách hàng' : 'Sửa thông tin khách hàng'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Lưu"
        cancelText="Hủy"
        confirmLoading={saving}
        forceRender
      >
        <Form<RestaurantForm> form={form} layout="vertical" onFinish={saveRestaurant}>
          <Form.Item name="name" label="Tên khách hàng" rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập tên' }, { max: 150 }]}>
            <Input maxLength={150} />
          </Form.Item>
          <Form.Item name="customerType" label="Loại khách hàng" rules={[{ required: true, message: 'Vui lòng chọn loại khách hàng' }]}>
            <Select options={CUSTOMER_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="address" label="Địa chỉ" rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập địa chỉ' }, { max: 255 }]}>
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="phone" label="Số điện thoại" rules={[PHONE_RULE]}>
            <Input maxLength={20} inputMode="tel" />
          </Form.Item>
          <Form.Item name="contactName" label="Người liên hệ">
            <Input maxLength={100} placeholder="VD: Chị Lan - quản lý buồng phòng" />
          </Form.Item>
          <Space wrap size={16} style={{ width: '100%' }}>
            <Form.Item name="turnaroundHours" label="Cam kết trả đồ sau" rules={[{ required: true }]} style={{ minWidth: 200 }}>
              <Select options={TURNAROUND_OPTIONS.map((h) => ({ value: h, label: `${h} giờ` }))} />
            </Form.Item>
            <Form.Item name="paymentTermDays" label="Kỳ thanh toán" rules={[{ required: true }]} style={{ minWidth: 200 }}>
              <Select options={PAYMENT_TERM_OPTIONS.map((d) => ({ value: d, label: paymentTermLabel(d) }))} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title={accountFor ? `Cấp tài khoản cho ${accountFor.name}` : ''}
        open={accountFor !== null}
        onCancel={() => setAccountFor(null)}
        onOk={() => accountForm.submit()}
        okText="Tạo tài khoản"
        cancelText="Hủy"
        confirmLoading={saving}
        forceRender
      >
        <Form<AccountForm> form={accountForm} layout="vertical" onFinish={createAccount}>
          <Form.Item name="role" label="Vai trò" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'RESTAURANT_MANAGER', label: 'Quản lý phía khách hàng (xác nhận đơn + xem công nợ)' },
                { value: 'RESTAURANT_STAFF', label: 'Nhân viên phía khách hàng (xác nhận đơn)' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="username"
            label="Tên đăng nhập"
            rules={[
              { required: true, message: 'Vui lòng nhập tên đăng nhập' },
              { min: 3, max: 50, message: 'Từ 3 đến 50 ký tự' },
              { pattern: /^[a-zA-Z0-9._-]+$/, message: 'Chỉ gồm chữ không dấu, số, . _ -' },
            ]}
          >
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="fullName" label="Họ tên người dùng">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item
            name="password"
            label="Mật khẩu ban đầu"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }, { min: 8, max: 72, message: 'Từ 8 đến 72 ký tự' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
