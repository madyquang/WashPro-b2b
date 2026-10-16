import { Component, type ReactNode } from 'react'
import { Button, Result } from 'antd'

interface State {
  failed: boolean
}

/**
 * Bắt lỗi khi tải / hiển thị một trang (thường là mất mạng nên không tải được gói JS của trang chưa mở lần nào):
 * hiện thông báo kèm nút thử lại, thay vì cả ứng dụng thành màn hình trắng. Đặt key theo đường dẫn để đổi trang là thử lại.
 */
export default class PageErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  render() {
    if (!this.state.failed) return this.props.children
    const offline = !navigator.onLine
    return (
      <Result
        status="warning"
        title={offline ? 'Chưa mở được trang này khi mất mạng' : 'Không tải được trang'}
        subTitle={offline ? 'Kết nối mạng lại rồi thử lại. Các trang đã mở trước đó vẫn xem được.' : 'Vui lòng tải lại trang.'}
        extra={
          <Button type="primary" onClick={() => window.location.reload()}>
            Thử lại
          </Button>
        }
      />
    )
  }
}
