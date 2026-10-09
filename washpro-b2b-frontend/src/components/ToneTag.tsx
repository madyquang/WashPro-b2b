import type { ReactNode } from 'react'
import { Tag } from 'antd'
import { STATUS_TONES, type StatusTone } from '../theme'

/** Nhãn chữ đậm trên nền nhạt theo bảng STATUS_TONES (luôn đạt tương phản ≥ 5:1). */
export default function ToneTag({ tone, icon, children }: { tone: StatusTone; icon?: ReactNode; children: ReactNode }) {
  const { fg, bg } = STATUS_TONES[tone]
  return (
    <Tag icon={icon} variant="filled" className="status-tag" style={{ color: fg, background: bg }}>
      {children}
    </Tag>
  )
}
