import type { ReactNode } from 'react'
import { CheckCircleOutlined, CloseCircleOutlined, HourglassOutlined, UserOutlined } from '@ant-design/icons'
import ToneTag from './ToneTag'
import type { StatusTone } from '../theme'
import { PICKUP_STATUS_META, type PickupStatus } from '../types'

/** Mỗi trạng thái: sắc thái màu đạt tương phản + icon, để không phải dựa vào màu mới phân biệt được. */
const PICKUP_STATUS_LOOK: Record<PickupStatus, { tone: StatusTone; icon: ReactNode }> = {
  OPEN: { tone: 'gold', icon: <HourglassOutlined /> },
  ACCEPTED: { tone: 'blue', icon: <UserOutlined /> },
  DONE: { tone: 'green', icon: <CheckCircleOutlined /> },
  CANCELLED: { tone: 'gray', icon: <CloseCircleOutlined /> },
}

export default function PickupStatusTag({ status }: { status: PickupStatus }) {
  const { tone, icon } = PICKUP_STATUS_LOOK[status]
  return (
    <ToneTag tone={tone} icon={icon}>
      {PICKUP_STATUS_META[status].label}
    </ToneTag>
  )
}
