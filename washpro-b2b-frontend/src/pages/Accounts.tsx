import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Form, Input, Modal, Popconfirm, Select, Space } from 'antd'
import { message } from '../utils/message'
import type { TableColumnsType } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import PageHeader from '../components/PageHeader'
import ResponsiveTable from '../components/ResponsiveTable'
import ToneTag from '../components/ToneTag'
import { getSession, isCustomerRole } from '../utils/auth'
import { formatDate, getErrorMessage } from '../utils/format'
import { ROLE_LABELS, type Customer, type Role, type UserAccount } from '../types'

interface CreateForm {
  username: string
  password: string
  fullName?: string
  phone?: string
  role: Role
  customerId?: number
}

/** Trạng thái tài khoản: chữ + màu đạt tương phản. */
const enabledTag = (enabled: boolean) => (enabled ? <ToneTag tone="green">Hoạt động</ToneTag> : <ToneTag tone="red">Đã khóa</ToneTag>)

const ROLE_OPTIONS = (Object.keys(ROLE_LABELS) as Role[]).map((r) => ({ value: r, label: ROLE_LABELS[r] }))

export default function Accounts() {
  const me = getSession()
  const [rows, setRows] = useState<UserAccount[]>([])
  const [customers, setCustomers] = useState<Customer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<Role | undefined>()

  const [creating, setCreating] = useState(false)
  const [resetFor, setResetFor] = useState<UserAccount | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<CreateForm>()
  const [resetForm] = Form.useForm<{ newPassword: string }>()
  const selectedRole = Form.useWatch('role', form)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [users, rest] = await Promise.all([
        axiosClient.get<UserAccount[]>('/users'),
        axiosClient.get<Customer[]>('/customers'),
      ])
      setRows(users.data)
      setCustomers(rest.data.filter((r) => r.active))
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được danh sách tài khoản'))
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
      (u) =>
        (!roleFilter || u.role === roleFilter) &&
        (!k ||
          u.username.toLowerCase().includes(k) ||
          (u.fullName ?? '').toLowerCase().includes(k) ||
          (u.customerName ?? '').toLowerCase().includes(k)),
    )
  }, [rows, search, roleFilter])

  const create = async (values: CreateForm) => {
    setSaving(true)
    try {
      await axiosClient.post('/users', {
        username: values.username.trim(),
        password: values.password,
        fullName: values.fullName?.trim() || null,
        phone: values.phone?.trim() || null,
        role: values.role,
        customerId: isCustomerRole(values.role) ? values.customerId : null,
      })
      message.success('Đã tạo tài khoản')
      setCreating(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const setEnabled = async (u: UserAccount, enabled: boolean) => {
    try {
      await axiosClient.patch(`/users/${u.id}/enabled`, { enabled })
      message.success(enabled ? `Đã mở khóa ${u.username}` : `Đã khóa ${u.username}`)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const resetPassword = async (values: { newPassword: string }) => {
    if (!resetFor) return
    setSaving(true)
    try {
      await axiosClient.put(`/users/${resetFor.id}/password`, { newPassword: values.newPassword })
      message.success(`Đã đặt lại mật khẩu cho ${resetFor.username}`)
      setResetFor(null)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const columns: TableColumnsType<UserAccount> = [
    { title: 'Tên đăng nhập', dataIndex: 'username' },
    { title: 'Họ tên', dataIndex: 'fullName', responsive: ['md'], render: (v: string | null) => v || '—' },
    { title: 'Vai trò', dataIndex: 'role', render: (r: Role) => <ToneTag tone="navy">{ROLE_LABELS[r]}</ToneTag> },
    { title: 'Khách hàng', dataIndex: 'customerName', responsive: ['md'], render: (v: string | null) => v || '—' },
    { title: 'Ngày tạo', dataIndex: 'createdAt', responsive: ['lg'], render: (v: string) => formatDate(v) },
    {
      title: 'Trạng thái',
      dataIndex: 'enabled',
      width: 110,
      render: (e: boolean) => enabledTag(e),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      render: (_: unknown, u) => (
        <Space size={4} wrap>
          <Button
            size="small"
            onClick={() => {
              resetForm.resetFields()
              setResetFor(u)
            }}
          >
            Đặt lại mật khẩu
          </Button>
          {u.enabled ? (
            <Popconfirm
              title={`Khóa tài khoản ${u.username}?`}
              description="Người này sẽ không đăng nhập được nữa."
              okText="Khóa"
              cancelText="Đóng"
              disabled={u.username === me?.username}
              onConfirm={() => setEnabled(u, false)}
            >
              <Button size="small" danger type="text" disabled={u.username === me?.username}>
                Khóa
              </Button>
            </Popconfirm>
          ) : (
            <Button size="small" type="text" onClick={() => setEnabled(u, true)}>
              Mở khóa
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const openCreate = () => {
    form.resetFields()
    form.setFieldsValue({ role: 'DRIVER' })
    setCreating(true)
  }

  return (
    <>
      <PageHeader
        title="Tài khoản"
        description="Tài khoản của tài xế, kế toán, quản lý xưởng và người dùng phía khách hàng."
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              Làm mới
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              Tạo tài khoản
            </Button>
          </>
        }
      />

      <div className="filter-bar">
        <Input.Search allowClear aria-label="Tìm tài khoản" placeholder="Tìm tên đăng nhập, họ tên, khách hàng" style={{ width: 280 }} onChange={(e) => setSearch(e.target.value)} />
        <Select allowClear aria-label="Lọc theo vai trò" placeholder="Lọc theo vai trò" style={{ width: 200 }} options={ROLE_OPTIONS} value={roleFilter} onChange={setRoleFilter} />
      </div>

      <LoadError error={error} onRetry={load} />

      <ResponsiveTable<UserAccount>
        table={{
          rowKey: 'id',
          loading,
          columns,
          dataSource: visible,
          scroll: { x: 'max-content' },
          pagination: { pageSize: 10, showSizeChanger: false, showTotal: (t) => `${t} tài khoản` },
        }}
        cardTitle={(u) => u.username}
        cardExtra={(u) => enabledTag(u.enabled)}
        hideInCard={['username', 'enabled']}
        emptyText={search || roleFilter ? 'Không có tài khoản nào khớp bộ lọc.' : 'Chưa có tài khoản nào.'}
      />

      <Modal
        title="Tạo tài khoản mới"
        open={creating}
        onCancel={() => setCreating(false)}
        onOk={() => form.submit()}
        okText="Tạo tài khoản"
        cancelText="Hủy"
        confirmLoading={saving}
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
        forceRender
      >
        <Form<CreateForm> form={form} layout="vertical" onFinish={create}>
          <Form.Item name="role" label="Vai trò" rules={[{ required: true, message: 'Vui lòng chọn vai trò' }]}>
            <Select options={ROLE_OPTIONS} />
          </Form.Item>
          {selectedRole && isCustomerRole(selectedRole) && (
            <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}>
              <Select
                showSearch={{ optionFilterProp: 'label' }}
                placeholder="Chọn khách hàng"
                options={customers.map((r) => ({ value: r.id, label: r.name }))}
              />
            </Form.Item>
          )}
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
          <Form.Item name="fullName" label="Họ tên">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ pattern: /^$|^[0-9+()\-. ]{8,20}$/, message: 'Số điện thoại không hợp lệ' }]}>
            <Input maxLength={20} inputMode="tel" />
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

      <Modal
        title={resetFor ? `Đặt lại mật khẩu: ${resetFor.username}` : ''}
        open={resetFor !== null}
        onCancel={() => setResetFor(null)}
        onOk={() => resetForm.submit()}
        okText="Đặt lại"
        cancelText="Hủy"
        confirmLoading={saving}
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
        forceRender
      >
        <Form form={resetForm} layout="vertical" onFinish={resetPassword}>
          <Form.Item
            name="newPassword"
            label="Mật khẩu mới"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu mới' }, { min: 8, max: 72, message: 'Từ 8 đến 72 ký tự' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
