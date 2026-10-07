import type { ThemeConfig } from 'antd'

/**
 * Nhận diện THANHXUAN, lấy từ logo (giọt nước xanh ngọc + sóng xanh đậm).
 * primary là bản đậm hơn của xanh ngọc trong logo để chữ trắng trên nút đủ tương phản (≥ 4.5:1).
 */
export const BRAND = {
  name: 'THANHXUAN',
  tagline: 'Giặt là công nghiệp',
  navy: '#124D6B',
  navyDeep: '#0C3247',
  teal: '#14A0A1',
  primary: '#0E8385',
  primarySoft: '#E6F4F4',
  bg: '#F3F6F8',
  ink: '#1B2B36',
  muted: '#5B6B76',
  border: '#E3E9ED',
} as const

/** Màu biểu đồ tuổi nợ (đã kiểm tra phân biệt màu, kể cả người mù màu). */
export const AGING_COLORS = {
  notDue: '#0A9FA3',
  d30: '#E8A33A',
  d60: '#D0602A',
  over60: '#8F2410',
} as const

export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: BRAND.primary,
    colorInfo: BRAND.primary,
    colorLink: BRAND.primary,
    colorTextBase: BRAND.ink,
    colorBgLayout: BRAND.bg,
    colorBorderSecondary: BRAND.border,
    borderRadius: 10,
    borderRadiusLG: 14,
    fontFamily: "'Be Vietnam Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif",
    fontSize: 14,
    controlHeight: 36,
  },
  components: {
    Layout: {
      siderBg: BRAND.navyDeep,
      headerBg: '#FFFFFF',
      headerHeight: 64,
      headerPadding: '0 20px',
      bodyBg: BRAND.bg,
    },
    Menu: {
      darkItemBg: BRAND.navyDeep,
      darkSubMenuItemBg: BRAND.navyDeep,
      darkItemColor: 'rgba(255,255,255,0.72)',
      darkItemHoverColor: '#FFFFFF',
      darkItemHoverBg: 'rgba(255,255,255,0.06)',
      darkItemSelectedBg: 'rgba(20,160,161,0.22)',
      darkItemSelectedColor: '#FFFFFF',
      itemBorderRadius: 10,
      itemMarginInline: 12,
      itemHeight: 44,
      iconSize: 17,
    },
    Card: {
      headerFontSize: 15,
    },
    Table: {
      headerBg: '#F6F9FA',
      headerColor: BRAND.muted,
      rowHoverBg: '#F2F8F8',
    },
    Button: {
      fontWeight: 500,
      primaryShadow: 'none',
    },
    Tabs: {
      itemSelectedColor: BRAND.primary,
      inkBarColor: BRAND.primary,
    },
    Statistic: {
      contentFontSize: 26,
    },
  },
}
