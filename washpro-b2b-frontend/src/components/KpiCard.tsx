import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Card } from 'antd'

type Tone = 'teal' | 'navy' | 'green' | 'orange' | 'red' | 'gray'

/** Màu nền / màu biểu tượng cho từng sắc thái (biểu tượng luôn kèm nhãn chữ, không dùng màu đơn lẻ). */
const TONES: Record<Tone, { bg: string; fg: string }> = {
  teal: { bg: '#E6F4F4', fg: '#0E8385' },
  navy: { bg: '#E7EEF3', fg: '#124D6B' },
  green: { bg: '#EAF6EC', fg: '#2F8A3E' },
  orange: { bg: '#FDF1E3', fg: '#B5620E' },
  red: { bg: '#FDECEA', fg: '#C2341B' },
  gray: { bg: '#EEF1F3', fg: '#5B6B76' },
}

interface Props {
  icon: ReactNode
  label: string
  value: ReactNode
  hint?: ReactNode
  tone?: Tone
  /** Màu chữ của giá trị (chỉ dùng khi cần nhấn mạnh, ví dụ số tiền quá hạn). */
  valueColor?: string
  to?: string
}

/** Thẻ chỉ số: biểu tượng trong ô màu + nhãn + giá trị lớn. */
export default function KpiCard({ icon, label, value, hint, tone = 'teal', valueColor, to }: Props) {
  const t = TONES[tone]
  const card = (
    <Card size="small" hoverable={!!to} style={{ height: '100%' }}>
      <div className="kpi">
        <div className="kpi-icon" style={{ background: t.bg, color: t.fg }}>
          {icon}
        </div>
        <div style={{ minWidth: 0 }}>
          <div className="kpi-label">{label}</div>
          <div className="kpi-value" style={valueColor ? { color: valueColor } : undefined}>
            {value}
          </div>
          {hint && <div className="kpi-hint">{hint}</div>}
        </div>
      </div>
    </Card>
  )
  return to ? (
    <Link to={to} style={{ display: 'block', height: '100%' }}>
      {card}
    </Link>
  ) : (
    card
  )
}
