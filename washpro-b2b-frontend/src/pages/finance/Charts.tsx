import { Card, Empty, Tooltip, Typography } from 'antd'
import { AGING_COLORS } from '../../theme'
import { formatCurrency } from '../../utils/format'
import type { DebtRow, MonthRevenue } from '../../types'

const monthLabel = (ym: string) => `T${Number(ym.slice(5))}/${ym.slice(2, 4)}`

/** Doanh thu theo tháng: một chuỗi, cột một màu; rê chuột / chạm vào cột để xem số liệu. */
export function RevenueChart({ months }: { months: MonthRevenue[] }) {
  const max = Math.max(...months.map((m) => m.revenue), 1)
  const latest = months[months.length - 1]
  const total = months.reduce((s, m) => s + m.revenue, 0)

  return (
    <Card
      title="Doanh thu 6 tháng gần nhất"
      style={{ height: '100%' }}
      extra={latest && <Typography.Text type="secondary">Tháng này: {formatCurrency(latest.revenue)}</Typography.Text>}
    >
      {total === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có đơn hoàn thành trong 6 tháng qua" />
      ) : (
        <>
          <div className="bar-chart" role="list" aria-label="Doanh thu theo tháng">
            {months.map((m) => (
              <Tooltip
                key={m.month}
                title={
                  <>
                    <div>Tháng {monthLabel(m.month).slice(1)}</div>
                    <b>{formatCurrency(m.revenue)}</b> · {m.orders} đơn
                  </>
                }
              >
                <div className="bar-col" role="listitem" tabIndex={0} aria-label={`${monthLabel(m.month)}: ${formatCurrency(m.revenue)}, ${m.orders} đơn`}>
                  <div className="bar" style={{ height: `${(m.revenue / max) * 100}%` }} />
                </div>
              </Tooltip>
            ))}
          </div>
          <div className="bar-labels" aria-hidden>
            {months.map((m) => (
              <span key={m.month}>{monthLabel(m.month)}</span>
            ))}
          </div>
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 10, fontSize: 13 }}>
            Tổng 6 tháng: <b style={{ color: 'var(--ink)' }}>{formatCurrency(total)}</b> · tính theo ngày đơn hoàn thành
          </Typography.Text>
        </>
      )}
    </Card>
  )
}

/** Tuổi nợ toàn bộ khách hàng: một thanh xếp chồng theo mức quá hạn, kèm chú thích có số tiền. */
export function AgingCard({ rows }: { rows: DebtRow[] }) {
  const sum = (pick: (r: DebtRow) => number) => rows.reduce((s, r) => s + Math.max(0, pick(r)), 0)
  const parts = [
    { key: 'notDue', label: 'Chưa đến hạn', value: sum((r) => r.notDue), color: AGING_COLORS.notDue },
    { key: 'd30', label: 'Quá hạn 1–30 ngày', value: sum((r) => r.overdue1to30), color: AGING_COLORS.d30 },
    { key: 'd60', label: 'Quá hạn 31–60 ngày', value: sum((r) => r.overdue31to60), color: AGING_COLORS.d60 },
    { key: 'over60', label: 'Quá hạn trên 60 ngày', value: sum((r) => r.overdueOver60), color: AGING_COLORS.over60 },
  ]
  const total = parts.reduce((s, p) => s + p.value, 0)
  const overdueCustomers = rows.filter((r) => r.overdueTotal > 0).length

  return (
    <Card title="Tuổi nợ" style={{ height: '100%' }}>
      {total === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có khoản nợ nào" />
      ) : (
        <>
          <Typography.Text type="secondary">Tổng còn nợ</Typography.Text>
          <div className="kpi-value" style={{ marginBottom: 12 }}>
            {formatCurrency(total)}
          </div>
          <div className="stack-bar" role="img" aria-label={parts.map((p) => `${p.label}: ${formatCurrency(p.value)}`).join('; ')}>
            {parts
              .filter((p) => p.value > 0)
              .map((p) => (
                <Tooltip key={p.key} title={`${p.label}: ${formatCurrency(p.value)} (${Math.round((p.value / total) * 100)}%)`}>
                  <div style={{ flexGrow: p.value, background: p.color }} />
                </Tooltip>
              ))}
          </div>
          <div className="legend">
            {parts.map((p) => (
              <div className="legend-item" key={p.key}>
                <span className="legend-swatch" style={{ background: p.color }} />
                <span>{p.label}</span>
                <b>{formatCurrency(p.value)}</b>
              </div>
            ))}
          </div>
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 13 }}>
            {overdueCustomers > 0 ? `${overdueCustomers} khách hàng đang có nợ quá hạn` : 'Chưa có khách hàng nào quá hạn'}
          </Typography.Text>
        </>
      )}
    </Card>
  )
}
