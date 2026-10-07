import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Descriptions, Form, Input, Tag, Typography, message } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import { clearSession, getSession } from '../utils/auth'
import { getErrorMessage } from '../utils/format'
import { ROLE_LABELS } from '../types'

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
      await axiosClient.post('/auth/change-password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      message.success('Đã đổi mật khẩu')
      form.resetFields()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ maxWidth: 560 }}>
      <Typography.Title level={3} className="page-title">
        Thông tin tài khoản
      </Typography.Title>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Tên đăng nhập">{session.username}</Descriptions.Item>
          <Descriptions.Item label="Họ tên">{session.fullName}</Descriptions.Item>
          <Descriptions.Item label="Vai trò">
            <Tag color="cyan">{ROLE_LABELS[session.role]}</Tag>
          </Descriptions.Item>
          {session.restaurantName && <Descriptions.Item label="Khách hàng">{session.restaurantName}</Descriptions.Item>}
        </Descriptions>
      </Card>

      <Card size="small" title="Đổi mật khẩu">
        {error && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={error} />}
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
          <Button type="primary" htmlType="submit" loading={saving}>
            Đổi mật khẩu
          </Button>
        </Form>
      </Card>

      <Button
        danger
        block
        icon={<LogoutOutlined />}
        style={{ marginTop: 16 }}
        onClick={() => {
          clearSession()
          navigate('/login', { replace: true })
        }}
      >
        Đăng xuất
      </Button>
    </div>
  )
}
