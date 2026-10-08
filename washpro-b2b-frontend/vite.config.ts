import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rolldownOptions: {
      output: {
        // React ít thay đổi nên tách gói riêng để trình duyệt giữ cache qua các lần cập nhật ứng dụng.
        // Không gom antd: từng thành phần antd đi theo trang dùng nó (trang đăng nhập không phải tải bảng, biểu mẫu...)
        codeSplitting: {
          groups: [{ name: 'react', test: /node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/ }],
        },
      },
    },
  },
})
