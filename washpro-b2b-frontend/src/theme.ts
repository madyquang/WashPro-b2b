import type { ThemeConfig } from 'antd'

/**
 * Nguồn màu duy nhất của giao diện (DESIGN_PLAN.md mục 5.1). Biến CSS trong index.css được sinh từ đây
 * (applyCssVariables), component không viết mã màu trực tiếp.
 *
 * Nhận diện THANHXUAN lấy từ logo (giọt nước xanh ngọc + sóng xanh đậm).
 * primary là bản đậm hơn của xanh ngọc trong logo để chữ trắng trên nút đủ tương phản (≥ 4.5:1).
 */
export const BRAND = {
  name: 'THANHXUAN',
  tagline: 'Giặt là công nghiệp',
  navy: '#124D6B',
  navyDeep: '#0C3247',
  /** Chỉ dùng trang trí / biểu đồ, không dùng cho chữ. */
  teal: '#14A0A1',
  primary: '#0E8385',
  /** Hover nút chính; cũng là màu liên kết trên nền xám (primary trên nền xám chỉ 4.2:1). */
  primaryHover: '#0B6E70',
  primarySoft: '#E6F4F4',
  bg: '#F3F6F8',
  surface: '#FFFFFF',
  surfaceMuted: '#F6F9FA',
  ink: '#1B2B36',
  muted: '#5B6B76',
  border: '#E3E9ED',
  success: '#1E7234',
  warning: '#8A5A00',
  danger: '#B42318',
  info: '#1F4FB8',
} as const

/**
 * Sắc thái của nhãn trạng thái / nhóm / ô chỉ số: chữ đậm trên nền nhạt, chữ/nền ≥ 5:1 (WCAG AA cần 4.5:1).
 * Màu chữ cũng dùng làm chấm màu, viền nhấn (≥ 5.9:1 trên nền trắng).
 */
export const STATUS_TONES = {
  gold: { fg: '#8A5A00', bg: '#FFF4D6' },
  red: { fg: '#B42318', bg: '#FEEBE9' },
  blue: { fg: '#1F4FB8', bg: '#E8EFFD' },
  teal: { fg: '#0B6466', bg: '#E0F2F2' },
  orange: { fg: '#A2400B', bg: '#FDEADB' },
  green: { fg: '#1E7234', bg: '#E6F4E9' },
  purple: { fg: '#5B2BAE', bg: '#F1EBFC' },
  navy: { fg: '#124D6B', bg: '#E7EEF3' },
  gray: { fg: '#4A5761', bg: '#EEF1F3' },
} as const

export type StatusTone = keyof typeof STATUS_TONES

/** Màu biểu đồ tuổi nợ: mỗi màu ≥ 3:1 trên nền trắng; các đoạn cách nhau 2px và chú thích luôn kèm số tiền. */
export const AGING_COLORS = {
  notDue: '#0A9FA3',
  d30: '#B57E0F',
  d60: '#D0602A',
  over60: '#8F2410',
} as const

/** Ghi màu ra biến CSS (--primary, --ink...) để index.css dùng chung một nguồn với theme antd. */
export function applyCssVariables(root: HTMLElement = document.documentElement) {
  const vars: Record<string, string> = {
    '--navy': BRAND.navy,
    '--navy-deep': BRAND.navyDeep,
    '--teal': BRAND.teal,
    '--primary': BRAND.primary,
    '--primary-hover': BRAND.primaryHover,
    '--primary-soft': BRAND.primarySoft,
    '--bg': BRAND.bg,
    '--surface': BRAND.surface,
    '--surface-muted': BRAND.surfaceMuted,
    '--ink': BRAND.ink,
    '--muted': BRAND.muted,
    '--border': BRAND.border,
    '--success': BRAND.success,
    '--warning': BRAND.warning,
    '--danger': BRAND.danger,
  }
  for (const [name, value] of Object.entries(vars)) root.style.setProperty(name, value)
}

/**
 * Cỡ điều khiển trên điện thoại: vùng bấm 44px cho thao tác một tay, chữ trong ô nhập 16px để iOS không tự phóng to.
 * Áp bằng component TouchFriendly, chỉ khi màn hình < 768px.
 */
export const touchTheme: ThemeConfig = {
  components: {
    // Nút nhỏ (thao tác trong thẻ / bảng) cũng đủ lớn để bấm bằng ngón tay
    Button: { controlHeight: 44, controlHeightSM: 36 },
    Input: { controlHeight: 44, inputFontSize: 16 },
    InputNumber: { controlHeight: 44, inputFontSize: 16 },
    Select: { controlHeight: 44, fontSize: 16 },
    Radio: { controlHeight: 44 },
  },
}

export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: BRAND.primary,
    colorInfo: BRAND.primary,
    colorLink: BRAND.primary,
    colorLinkHover: BRAND.primaryHover,
    // Màu mặc định của antd cho lỗi / cảnh báo chỉ đạt ~3:1 trên nền trắng
    colorError: BRAND.danger,
    colorWarning: BRAND.warning,
    colorSuccess: BRAND.success,
    colorWarningBg: STATUS_TONES.gold.bg,
    colorWarningBorder: '#F0D48A',
    colorErrorBg: STATUS_TONES.red.bg,
    colorErrorBorder: '#F4C0BA',
    colorSuccessBg: STATUS_TONES.green.bg,
    colorSuccessBorder: '#B7DCC1',
    colorInfoBg: BRAND.primarySoft,
    colorInfoBorder: '#B5DCDC',
    colorTextBase: BRAND.ink,
    colorTextSecondary: BRAND.muted,
    // antd dùng màu này cho mô tả (List.Item.Meta, Descriptions...): mặc định chỉ 2.66:1
    colorTextDescription: BRAND.muted,
    colorBgLayout: BRAND.bg,
    colorBorderSecondary: BRAND.border,
    borderRadius: 10,
    borderRadiusLG: 14,
    borderRadiusSM: 6,
    fontFamily: "'Be Vietnam Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif",
    fontSize: 14,
    controlHeight: 36,
    motionDurationMid: '0.15s',
    motionDurationSlow: '0.25s',
  },
  components: {
    Layout: {
      siderBg: BRAND.navyDeep,
      headerBg: BRAND.surface,
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
      headerBg: BRAND.surfaceMuted,
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
