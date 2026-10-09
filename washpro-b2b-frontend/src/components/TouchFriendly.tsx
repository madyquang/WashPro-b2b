import type { ReactNode } from 'react'
import { ConfigProvider, Grid } from 'antd'
import { touchTheme } from '../theme'

/** Trên điện thoại (< 768px): nút, ô nhập cao 44px và chữ ô nhập 16px. Máy tính giữ nguyên cỡ mặc định. */
export default function TouchFriendly({ children }: { children: ReactNode }) {
  const screens = Grid.useBreakpoint()
  // Lần render đầu useBreakpoint có thể chưa có số đo: coi là máy tính để không nhảy bố cục trên máy tính
  const mobile = screens.md === false
  return mobile ? <ConfigProvider theme={touchTheme}>{children}</ConfigProvider> : <>{children}</>
}
