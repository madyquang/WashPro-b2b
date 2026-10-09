import ToneTag from './ToneTag'
import { ORDER_STATUS_LOOK } from '../utils/statusLook'
import { STATUS_META, type OrderStatus } from '../types'

/** Nhãn trạng thái đơn: chữ + icon + màu đạt tương phản (màu lấy từ utils/statusLook). */
export default function StatusTag({ status }: { status: OrderStatus }) {
  const { tone, icon } = ORDER_STATUS_LOOK[status]
  return (
    <ToneTag tone={tone} icon={icon}>
      {STATUS_META[status].label}
    </ToneTag>
  )
}
