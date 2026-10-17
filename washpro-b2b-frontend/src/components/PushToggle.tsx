import { useEffect, useState } from 'react'
import { Space, Switch, Typography } from 'antd'
import { message } from '../utils/message'
import { getErrorMessage } from '../utils/format'
import { disablePush, enablePush, pushState, type PushState } from '../utils/push'
import { isIos, isStandalone } from '../utils/pwa'

/** Bật / tắt thông báo đẩy trên thiết bị đang dùng (trang Tài khoản). */
export default function PushToggle() {
  const [state, setState] = useState<PushState | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    pushState()
      .then(setState)
      .catch(() => setState('unsupported'))
  }, [])

  const change = async (on: boolean) => {
    setBusy(true)
    try {
      const next = on ? await enablePush() : await disablePush()
      setState(next)
      if (on && next === 'on') message.success('Đã bật thông báo trên thiết bị này')
      if (on && next === 'blocked') message.warning('Trình duyệt đang chặn thông báo của trang này')
    } catch (e) {
      message.error(getErrorMessage(e, 'Không bật được thông báo trên thiết bị này'))
    } finally {
      setBusy(false)
    }
  }

  if (state === null) return null
  if (state === 'unsupported') {
    return (
      <Typography.Text type="secondary">
        {isIos() && !isStandalone()
          ? 'Trên iPhone / iPad: cài ứng dụng lên màn hình chính (mục bên trên) rồi mở từ biểu tượng để bật thông báo.'
          : 'Trình duyệt này chưa hỗ trợ thông báo đẩy. Dùng Chrome / Edge / Safari bản mới hoặc ứng dụng đã cài.'}
      </Typography.Text>
    )
  }
  return (
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Space align="center">
        <Switch checked={state === 'on'} loading={busy} disabled={state === 'blocked'} onChange={change} aria-label="Thông báo trên thiết bị này" />
        <Typography.Text>{state === 'on' ? 'Đang bật trên thiết bị này' : 'Đang tắt trên thiết bị này'}</Typography.Text>
      </Space>
      <Typography.Text type="secondary">
        {state === 'blocked'
          ? 'Bạn đã chặn thông báo của trang này. Mở cài đặt trang (biểu tượng ổ khóa cạnh địa chỉ) → Thông báo → Cho phép, rồi tải lại trang.'
          : 'Nhận ngay việc mới (yêu cầu lấy đồ, đơn chờ xác nhận, đồ đang giao...) cả khi không mở ứng dụng. Mỗi điện thoại / máy tính bật riêng.'}
      </Typography.Text>
    </Space>
  )
}
