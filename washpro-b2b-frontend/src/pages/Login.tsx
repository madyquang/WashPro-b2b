import { useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Alert, Button, Form, Input, Typography } from 'antd'
import { CheckCircleFilled, LockOutlined, UserOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import logo from '../assets/logo-mark.png'
import { BRAND } from '../theme'
import { getSession, saveSession } from '../utils/auth'
import { getErrorMessage } from '../utils/format'
import type { Session } from '../types'

interface LoginValues {
  username: string
  password: string
}

const POINTS = [
  'Khách hàng gửi yêu cầu lấy đồ, theo dõi đơn và xác nhận nhận đồ ngay trên điện thoại',
  'Tài xế nhận việc, đếm đồ theo từng loại và tạo đơn tại chỗ',
  'Xưởng nắm hạn trả, công nợ và sao kê của từng khách hàng',
]

export default function Login() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (getSession()) return <Navigate to="/" replace />

  const onFinish = async (values: LoginValues) => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.post<Session>('/auth/login', {
        username: values.username.trim(),
        password: values.password,
      })
      saveSession(data)
      navigate('/', { replace: true })
    } catch (e) {
      setError(getErrorMessage(e, 'Đăng nhập thất bại'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <section className="login-brand">
        <div>
          <div className="brand" style={{ padding: 0 }}>
            <div className="brand-logo" aria-hidden>
              <img src={logo} alt="" />
            </div>
            <div>
              <div className="brand-name">{BRAND.name}</div>
              <div className="brand-tagline">{BRAND.tagline}</div>
            </div>
          </div>
          <p className="login-slogan">Sạch tinh tươm, đúng hẹn, minh bạch từng món đồ.</p>
          <p className="login-desc">Hệ thống quản lý giặt là cho nhà hàng, khách sạn, spa và doanh nghiệp.</p>
          <div className="login-points">
            {POINTS.map((p) => (
              <div className="login-point" key={p}>
                <CheckCircleFilled aria-hidden />
                <span>{p}</span>
              </div>
            ))}
          </div>
        </div>
        <span className="login-copyright">
          © {new Date().getFullYear()} {BRAND.name}
        </span>
      </section>

      <section className="login-form-side">
        <div className="login-form">
          <h1 className="login-title">Đăng nhập</h1>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 28 }}>
            Dùng tài khoản do xưởng {BRAND.name} cấp.
          </Typography.Paragraph>

          {params.get('expired') && !error && (
            <Alert type="warning" showIcon className="mb-4" title="Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại" />
          )}
          {error && <Alert type="error" showIcon className="mb-4" title={error} />}

          <Form<LoginValues> layout="vertical" onFinish={onFinish} autoComplete="on" requiredMark={false} size="large">
            <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập tên đăng nhập' }]}>
              <Input prefix={<UserOutlined />} autoComplete="username" autoCapitalize="none" autoFocus />
            </Form.Item>
            <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}>
              <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading} style={{ marginTop: 8, height: 48 }}>
              Đăng nhập
            </Button>
          </Form>
          <Typography.Paragraph type="secondary" style={{ marginTop: 20, fontSize: 13 }}>
            Quên mật khẩu? Liên hệ quản lý xưởng để được đặt lại.
          </Typography.Paragraph>
        </div>
      </section>
    </div>
  )
}
