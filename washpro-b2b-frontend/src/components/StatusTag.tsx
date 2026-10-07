import { Tag } from 'antd'
import { STATUS_META, type OrderStatus } from '../types'

export default function StatusTag({ status }: { status: OrderStatus }) {
  const meta = STATUS_META[status]
  return <Tag color={meta.color}>{meta.label}</Tag>
}
