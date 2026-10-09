import { Tooltip } from 'antd'
import { ClockCircleOutlined, WarningOutlined } from '@ant-design/icons'
import ToneTag from './ToneTag'
import { formatDateTime } from '../utils/format'
import type { Order } from '../types'

const HOUR = 3600_000

/** "3 giờ" / "2 ngày" */
const span = (ms: number) => {
  const hours = Math.max(1, Math.round(Math.abs(ms) / HOUR))
  return hours < 48 ? `${hours} giờ` : `${Math.round(hours / 24)} ngày`
}

/** Hạn trả đồ của đơn: trễ (đỏ), sắp đến hạn trong 6 giờ (cam), còn lại hiện ngày giờ. */
export default function DueTag({ order }: { order: Pick<Order, 'dueAt' | 'overdue' | 'status'> }) {
  if (!order.dueAt || order.status === 'COMPLETED') return <span>—</span>
  const left = new Date(order.dueAt).getTime() - Date.now()
  const title = `Hạn trả: ${formatDateTime(order.dueAt)}`
  if (order.overdue || left < 0) {
    return (
      <Tooltip title={title}>
        <span>
          <ToneTag tone="red" icon={<WarningOutlined />}>
            Trễ {span(left)}
          </ToneTag>
        </span>
      </Tooltip>
    )
  }
  if (left < 6 * HOUR) {
    return (
      <Tooltip title={title}>
        <span>
          <ToneTag tone="orange" icon={<ClockCircleOutlined />}>
            Còn {span(left)}
          </ToneTag>
        </span>
      </Tooltip>
    )
  }
  const d = new Date(order.dueAt)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    <Tooltip title={title}>
      <span style={{ whiteSpace: 'nowrap' }}>{`${pad(d.getHours())}:${pad(d.getMinutes())} ${pad(d.getDate())}/${pad(d.getMonth() + 1)}`}</span>
    </Tooltip>
  )
}
