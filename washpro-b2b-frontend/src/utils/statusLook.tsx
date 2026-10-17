import type { ReactNode } from 'react'
import {
  CarOutlined,
  CheckCircleOutlined,
  CheckSquareOutlined,
  ExclamationCircleOutlined,
  HistoryOutlined,
  HourglassOutlined,
  InboxOutlined,
  SyncOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { STATUS_TONES, type StatusTone } from '../theme'
import type { OrderStatus } from '../types'

/** Nguồn màu + icon duy nhất cho trạng thái đơn (nhãn, chấm màu ở dải trạng thái, số đếm trên tab). */
export const ORDER_STATUS_LOOK: Record<OrderStatus, { tone: StatusTone; icon: ReactNode }> = {
  PICKUP_PENDING: { tone: 'gold', icon: <HourglassOutlined /> },
  PICKUP_ASSIGNED: { tone: 'navy', icon: <UserOutlined /> },
  RECEIVED: { tone: 'purple', icon: <InboxOutlined /> },
  WASHING: { tone: 'blue', icon: <SyncOutlined /> },
  WASHED: { tone: 'teal', icon: <CheckSquareOutlined /> },
  DELIVERING: { tone: 'teal', icon: <CarOutlined /> },
  DELIVERY_ISSUE: { tone: 'orange', icon: <WarningOutlined /> },
  COMPLETED: { tone: 'green', icon: <CheckCircleOutlined /> },
  PENDING_CONFIRM: { tone: 'gray', icon: <HistoryOutlined /> },
  DISCREPANCY: { tone: 'gray', icon: <ExclamationCircleOutlined /> },
}

/** Màu đậm của trạng thái (chấm màu, số đếm). */
export const statusColor = (status: OrderStatus) => STATUS_TONES[ORDER_STATUS_LOOK[status].tone].fg
