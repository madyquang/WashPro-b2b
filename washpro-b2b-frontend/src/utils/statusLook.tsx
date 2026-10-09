import type { ReactNode } from 'react'
import {
  CarOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  HourglassOutlined,
  SyncOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { STATUS_TONES, type StatusTone } from '../theme'
import type { OrderStatus } from '../types'

/** Nguồn màu + icon duy nhất cho trạng thái đơn (nhãn, chấm màu ở dải trạng thái, số đếm trên tab). */
export const ORDER_STATUS_LOOK: Record<OrderStatus, { tone: StatusTone; icon: ReactNode }> = {
  PENDING_CONFIRM: { tone: 'gold', icon: <HourglassOutlined /> },
  DISCREPANCY: { tone: 'red', icon: <ExclamationCircleOutlined /> },
  WASHING: { tone: 'blue', icon: <SyncOutlined /> },
  DELIVERING: { tone: 'teal', icon: <CarOutlined /> },
  DELIVERY_ISSUE: { tone: 'orange', icon: <WarningOutlined /> },
  COMPLETED: { tone: 'green', icon: <CheckCircleOutlined /> },
}

/** Màu đậm của trạng thái (chấm màu, số đếm). */
export const statusColor = (status: OrderStatus) => STATUS_TONES[ORDER_STATUS_LOOK[status].tone].fg
