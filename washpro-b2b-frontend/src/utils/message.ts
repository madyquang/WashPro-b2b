import { message as staticMessage } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'

/**
 * Thông báo nổi (toast) dùng đúng theme của ứng dụng.
 * Hàm tĩnh `message` của antd nằm ngoài ConfigProvider nên hiện màu mặc định (màu lỗi chỉ ~3:1, không phải màu
 * thương hiệu) và antd cảnh báo trong console. MessageBridge (App.tsx) gắn instance lấy từ App.useApp() vào đây;
 * trước khi gắn (rất ngắn, lúc khởi động) thì dùng tạm hàm tĩnh.
 */
let instance: MessageInstance | null = null

export const bindMessage = (m: MessageInstance) => {
  instance = m
}

const current = () => instance ?? staticMessage

export const message = {
  success: (content: string) => current().success(content),
  error: (content: string) => current().error(content),
  warning: (content: string) => current().warning(content),
  info: (content: string) => current().info(content),
}
