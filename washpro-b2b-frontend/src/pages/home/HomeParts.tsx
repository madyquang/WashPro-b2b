import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from 'antd'
import { statusColor } from '../../utils/statusLook'
import { STATUS_META, STATUS_ORDER, type OrderStatus } from '../../types'

/** Một khối trên trang chủ: tiêu đề h2 (kèm số việc) + liên kết "xem tất cả" + nội dung. */
export function Section({ title, count, extra, children }: { title: ReactNode; count?: number; extra?: ReactNode; children: ReactNode }) {
  return (
    <section className="home-section">
      <div className="home-section-head">
        <h2 className="section-title">
          {title}
          {count !== undefined && count > 0 && <Badge count={count} overflowCount={99} style={{ marginInlineStart: 8 }} />}
        </h2>
        {extra}
      </div>
      {children}
    </section>
  )
}

/** Dải số đơn theo trạng thái; mỗi ô mở danh sách đơn đã lọc. */
export function StatusStrip({ counts, linkFor }: { counts: Partial<Record<OrderStatus, number>>; linkFor: (s: OrderStatus) => string }) {
  return (
    <nav className="stat-strip status-strip" aria-label="Số đơn theo trạng thái">
      {STATUS_ORDER.map((s) => (
        <Link key={s} to={linkFor(s)} className="stat-cell">
          <span className="stat-value" style={{ display: 'block' }}>
            {counts[s] ?? 0}
          </span>
          <span className="stat-label">
            <span className="dot" style={{ background: statusColor(s) }} aria-hidden />
            {STATUS_META[s].label}
          </span>
        </Link>
      ))}
    </nav>
  )
}

/** Danh sách thẻ xếp lưới (1 cột điện thoại, 2 cột máy tính). */
export function CardGrid({ children, wide }: { children: ReactNode; wide?: boolean }) {
  return <div className={wide ? 'card-grid is-wide' : 'card-grid'}>{children}</div>
}
