import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Descriptions, Form, Input, Popconfirm } from 'antd'
import { message } from '../utils/message'
import { DisconnectOutlined, LogoutOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import PageHeader from '../components/PageHeader'
import ToneTag from '../components/ToneTag'
import { clearSession, getSession, saveSession } from '../utils/auth'
import { getErrorMessage } from '../utils/format'
import { ROLE_LABELS, type Session } from '../types'

interface PasswordForm {
  currentPassword: string
  newPassword: string
  confirm: string
}

export default function Profile() {
  const session = getSession()
  const navigate = useNavigate()
  const [form] = Form.useForm<PasswordForm>()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!session) return null

  const submit = async (values: PasswordForm) => {
    setSaving(true)
    setError(null)
    try {
      // Các thiết bị khác bị đăng xuất; thiết bị này nhận phiên mới để dùng tiếp
      const { data } = await axiosClient.post<Session>('/auth/change-password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      saveSession(data)
      message.success('Đã đổi mật khẩu. Các thiết bị khác đã bị đăng xuất')
      form.resetFields()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const logout = () => {
    clearSession()
    navigate('/login', { replace: true })
  }

  const logoutEverywhere = async () => {
    try {
      await axiosClient.post('/auth/logout-all')
      message.success('Đã đăng xuất khỏi mọi thiết bị')
      logout()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  return (
    <div className="narrow">
      <PageHeader title="Thông tin tài khoản" description="Thông tin đăng nhập và đổi mật khẩu của bạn." />

      <Card size="small" className="mb-4">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Tên đăng nhập">{session.username}</Descriptions.Item>
          <Descriptions.Item label="Họ tên">{session.fullName}</Descriptions.Item>
          <Descriptions.Item label="Vai trò">
            <ToneTag tone="navy">{ROLE_LABELS[session.role]}</ToneTag>
          </Descriptions.Item>
          {session.restaurantName && <Descriptions.Item label="Khách hàng">{session.restaurantName}</Descriptions.Item>}
        </Descriptions>
      </Card>

      <Card size="small" title="Đổi mật khẩu">
        {error && <Alert type="error" showIcon className="mb-4" title={error} />}
        <Form<PasswordForm> form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="currentPassword" label="Mật khẩu hiện tại" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu hiện tại' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="Mật khẩu mới"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu mới' }, { min: 8, max: 72, message: 'Từ 8 đến 72 ký tự' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="Nhập lại mật khẩu mới"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Vui lòng nhập lại mật khẩu mới' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error('Mật khẩu nhập lại không khớp')),
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={saving} block>
            Đổi mật khẩu
          </Button>
        </Form>
      </Card>

      <Button danger block icon={<LogoutOutlined />} style={{ marginTop: 16 }} onClick={logout}>
        Đăng xuất
      </Button>
      <Popconfirm
        title="Đăng xuất khỏi mọi thiết bị?"
        description="Mọi nơi đang đăng nhập tài khoản này (kể cả thiết bị này) phải đăng nhập lại."
        okText="Đăng xuất hết"
        cancelText="Không"
        onConfirm={logoutEverywhere}
      >
        <Button block type="text" icon={<DisconnectOutlined />} style={{ marginTop: 8 }}>
          Đăng xuất mọi thiết bị (khi mất máy / nghi lộ mật khẩu)
        </Button>
      </Popconfirm>
    </div>
  )
}
