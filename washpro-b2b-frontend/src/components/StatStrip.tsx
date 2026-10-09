import type { CSSProperties, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { STATUS_TONES, type StatusTone } from '../theme'

export interface Stat {
  key: string
  label: ReactNode
  value: ReactNode
  hint?: ReactNode
  icon?: ReactNode
  tone?: StatusTone
  /** Tô màu giá trị theo sắc thái (chỉ khi cần nhấn mạnh, ví dụ nợ quá hạn > 0). */
  emphasize?: boolean
  to?: string
}

interface Props {
  items: Stat[]
  /** Xếp dọc một cột (dùng ở cột hẹp bên phải). */
  stacked?: boolean
  'aria-label'?: string
}

/** Dải chỉ số: một thẻ chia ô thay cho nhiều thẻ rời (DESIGN_PLAN.md 5.5 – Card). */
export default function StatStrip({ items, stacked, 'aria-label': ariaLabel }: Props) {
  return (
    <section
      className={stacked ? 'stat-strip is-stacked' : 'stat-strip'}
      style={{ '--cols': items.length } as CSSProperties}
      aria-label={ariaLabel}
    >
      {items.map((s) => {
        const tone = STATUS_TONES[s.tone ?? 'teal']
        const body = (
          <>
            {s.icon && (
              <span className="stat-icon" style={{ background: tone.bg, color: tone.fg }} aria-hidden>
                {s.icon}
              </span>
            )}
            <span style={{ minWidth: 0 }}>
              <span className="stat-label" style={{ display: 'block' }}>
                {s.label}
              </span>
              <span className="stat-value" style={{ display: 'block', color: s.emphasize ? tone.fg : undefined }}>
                {s.value}
              </span>
              {s.hint && (
                <span className="stat-hint" style={{ display: 'block' }}>
                  {s.hint}
                </span>
              )}
            </span>
          </>
        )
        return s.to ? (
          <Link key={s.key} to={s.to} className="stat-cell">
            {body}
          </Link>
        ) : (
          <div key={s.key} className="stat-cell">
            {body}
          </div>
        )
      })}
    </section>
  )
}
