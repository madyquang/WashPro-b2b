import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Col, Form, Input, Modal, Popconfirm, Row, Select, Space } from 'antd'
import { message } from '../utils/message'
import type { TableColumnsType } from 'antd'
import { PlusOutlined, ReloadOutlined, UserAddOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import PageHeader from '../components/PageHeader'
import ResponsiveTable from '../components/ResponsiveTable'
import ToneTag from '../components/ToneTag'
import { formatDate, getErrorMessage } from '../utils/format'
import {
  CUSTOMER_TYPE_LABELS,
  PAYMENT_TERM_OPTIONS,
  TURNAROUND_OPTIONS,
  paymentTermLabel,
  type CustomerType,
  type Customer,
} from '../types'

const CUSTOMER_TYPE_OPTIONS = (Object.keys(CUSTOMER_TYPE_LABELS) as CustomerType[]).map((t) => ({ value: t, label: CUSTOMER_TYPE_LABELS[t] }))

interface CustomerForm {
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
  role: 'CUSTOMER_MANAGER' | 'CUSTOMER_STAFF'
}

const PHONE_RULE = { pattern: /^$|^[0-9+()\-. ]{8,20}$/, message: 'Số điện thoại không hợp lệ' }

export default function Customers() {
  const [rows, setRows] = useState<Customer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState<CustomerType | undefined>()

  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [accountFor, setAccountFor] = useState<Customer | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<CustomerForm>()
  const [accountForm] = Form.useForm<AccountForm>()

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.get<Customer[]>('/customers')
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
    return rows.filter(
      (r) => (!typeFilter || r.customerType === typeFilter) && (!k || r.name.toLowerCase().includes(k) || r.address?.toLowerCase().includes(k)),
    )
  }, [rows, search, typeFilter])

  const openEditor = (target: Customer | 'new') => {
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

  const saveCustomer = async (values: CustomerForm) => {
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
        await axiosClient.post('/customers', body)
        message.success('Đã thêm khách hàng')
      } else if (editing) {
        await axiosClient.put(`/customers/${editing.id}`, body)
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

  const toggleActive = async (r: Customer) => {
    try {
      await axiosClient.patch(`/customers/${r.id}/active`, { enabled: !r.active })
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
        customerId: accountFor.id,
      })
      message.success(`Đã cấp tài khoản cho ${accountFor.name}`)
      setAccountFor(null)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const columns: TableColumnsType<Customer> = [
    { title: 'Tên khách hàng', dataIndex: 'name', sorter: (a, b) => a.name.localeCompare(b.name, 'vi') },
    {
      title: 'Loại khách',
      dataIndex: 'customerType',
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
      render: (a: boolean) => (a ? <ToneTag tone="green">Đang hợp tác</ToneTag> : <ToneTag tone="gray">Ngừng hợp tác</ToneTag>),
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
                accountForm.setFieldsValue({ role: 'CUSTOMER_MANAGER' })
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
      <PageHeader
        title="Khách hàng"
        description="Nhà hàng, khách sạn, spa… đang hợp tác: thời gian trả đồ, kỳ thanh toán và tài khoản đăng nhập."
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              Làm mới
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('new')}>
              Thêm khách hàng
            </Button>
          </>
        }
      />

      <div className="filter-bar">
        <Input.Search allowClear aria-label="Tìm khách hàng" placeholder="Tìm theo tên, địa chỉ" style={{ width: 260 }} onChange={(e) => setSearch(e.target.value)} />
        <Select
          allowClear
          aria-label="Lọc theo loại khách"
          placeholder="Lọc theo loại khách"
          style={{ width: 200 }}
          options={CUSTOMER_TYPE_OPTIONS}
          value={typeFilter}
          onChange={setTypeFilter}
        />
      </div>

      <LoadError error={error} onRetry={load} />

      <ResponsiveTable<Customer>
        table={{
          rowKey: 'id',
          loading,
          columns,
          dataSource: visible,
          scroll: { x: 'max-content' },
          pagination: { pageSize: 10, showSizeChanger: false, showTotal: (t) => `${t} khách hàng` },
        }}
        cardTitle={(r) => r.name}
        cardExtra={(r) => (r.active ? <ToneTag tone="green">Đang hợp tác</ToneTag> : <ToneTag tone="gray">Ngừng hợp tác</ToneTag>)}
        hideInCard={['name', 'active']}
        emptyText={search || typeFilter ? 'Không có khách hàng nào khớp bộ lọc.' : 'Chưa có khách hàng nào.'}
        emptyAction={search || typeFilter ? undefined : { label: 'Thêm khách hàng', icon: <PlusOutlined />, onClick: () => openEditor('new') }}
      />

      <Modal
        title={editing === 'new' ? 'Thêm khách hàng' : 'Sửa thông tin khách hàng'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Lưu"
        cancelText="Hủy"
        confirmLoading={saving}
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
        forceRender
      >
        <Form<CustomerForm> form={form} layout="vertical" onFinish={saveCustomer}>
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
          {/* Hai trường ngắn liên quan: cạnh nhau trên máy tính, xếp dọc trên điện thoại */}
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="turnaroundHours" label="Cam kết trả đồ sau" rules={[{ required: true }]}>
                <Select options={TURNAROUND_OPTIONS.map((h) => ({ value: h, label: `${h} giờ` }))} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="paymentTermDays" label="Kỳ thanh toán" rules={[{ required: true }]}>
                <Select options={PAYMENT_TERM_OPTIONS.map((d) => ({ value: d, label: paymentTermLabel(d) }))} />
              </Form.Item>
            </Col>
          </Row>
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
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
        forceRender
      >
        <Form<AccountForm> form={accountForm} layout="vertical" onFinish={createAccount}>
          <Form.Item name="role" label="Vai trò" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'CUSTOMER_MANAGER', label: 'Quản lý phía khách hàng (xác nhận đơn + xem công nợ)' },
                { value: 'CUSTOMER_STAFF', label: 'Nhân viên phía khách hàng (xác nhận đơn)' },
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
