import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge, Button, Empty, Popover, Spin } from 'antd'
import {
  AlertOutlined,
  BellOutlined,
  CarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  FieldTimeOutlined,
  InboxOutlined,
} from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import { getSession } from '../utils/auth'
import { pushState } from '../utils/push'
import { getErrorMessage } from '../utils/format'
import type { AppNotification, NotificationType } from '../types'

/** Kiểm tra thông báo mới mỗi 30 giây (và ngay khi người dùng quay lại tab). */
const POLL_MS = 30_000

const ICONS: Record<NotificationType, ReactNode> = {
  PICKUP_NEW: <InboxOutlined />,
  PICKUP_ACCEPTED: <CarOutlined />,
  PICKUP_ASSIGNED: <CarOutlined />,
  PICKUP_CANCELLED: <CloseCircleOutlined />,
  ORDER_TO_CONFIRM: <CheckCircleOutlined />,
  ORDER_DISCREPANCY: <ExclamationCircleOutlined />,
  ORDER_DELIVERING: <CarOutlined />,
  ORDER_RECEIPT_ISSUE: <AlertOutlined />,
  ORDER_CANCELLED: <CloseCircleOutlined />,
  PAYMENT_RECORDED: <DollarOutlined />,
  OVERDUE_ORDERS: <FieldTimeOutlined />,
  OVERDUE_DEBT: <DollarOutlined />,
}

const WARN: NotificationType[] = ['ORDER_DISCREPANCY', 'ORDER_RECEIPT_ISSUE', 'OVERDUE_ORDERS', 'OVERDUE_DEBT', 'PICKUP_CANCELLED', 'ORDER_CANCELLED']

function timeAgo(iso: string) {
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60_000)
  if (minutes < 1) return 'Vừa xong'
  if (minutes < 60) return `${minutes} phút trước`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} giờ trước`
  const days = Math.floor(hours / 24)
  return days === 1 ? 'Hôm qua' : `${days} ngày trước`
}

export default function NotificationBell() {
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<AppNotification[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pushOff, setPushOff] = useState(false)

  const refreshCount = useCallback(async () => {
    if (!getSession() || document.visibilityState === 'hidden') return
    try {
      const { data } = await axiosClient.get<{ unread: number }>('/notifications/unread-count')
      setUnread(data.unread)
    } catch {
      // Lỗi mạng tạm thời: lần kiểm tra sau sẽ thử lại
    }
  }, [])

  useEffect(() => {
    refreshCount()
    const timer = setInterval(refreshCount, POLL_MS)
    document.addEventListener('visibilitychange', refreshCount)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', refreshCount)
    }
  }, [refreshCount])

  const loadList = useCallback(async () => {
    setError(null)
    try {
      const { data } = await axiosClient.get<{ items: AppNotification[]; unread: number }>('/notifications')
      setItems(data.items)
      setUnread(data.unread)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được thông báo'))
    }
  }, [])

  const toggle = (next: boolean) => {
    setOpen(next)
    if (next) {
      loadList()
      pushState()
        .then((s) => setPushOff(s === 'off'))
        .catch(() => setPushOff(false))
    }
  }

  const openItem = (n: AppNotification) => {
    setOpen(false)
    if (!n.read) {
      setItems((list) => list?.map((x) => (x.id === n.id ? { ...x, read: true } : x)) ?? null)
      setUnread((u) => Math.max(0, u - 1))
      axiosClient.post(`/notifications/${n.id}/read`).catch(() => {})
    }
    if (n.link) navigate(n.link)
  }

  const readAll = async () => {
    try {
      await axiosClient.post('/notifications/read-all')
      setItems((list) => list?.map((x) => ({ ...x, read: true })) ?? null)
      setUnread(0)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  const content = (
    <div className="notif-panel">
      <div className="notif-head">
        <strong>Thông báo</strong>
        {unread > 0 && (
          <Button type="link" size="small" onClick={readAll}>
            Đánh dấu đã đọc hết
          </Button>
        )}
      </div>
      {error && <div className="notif-error">{error}</div>}
      {pushOff && (
        <button
          type="button"
          className="notif-push-hint"
          onClick={() => {
            setOpen(false)
            navigate('/profile')
          }}
        >
          Bật thông báo trên điện thoại để biết việc mới ngay cả khi không mở ứng dụng →
        </button>
      )}
      {items === null && !error ? (
        <div className="notif-loading">
          <Spin />
        </div>
      ) : items && items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có thông báo nào" />
      ) : (
        <ul className="notif-list">
          {items?.map((n) => (
            <li key={n.id}>
              <button type="button" className={`notif-item${n.read ? '' : ' unread'}`} onClick={() => openItem(n)}>
                <span className={`notif-icon${WARN.includes(n.type) ? ' warn' : ''}`} aria-hidden>
                  {ICONS[n.type] ?? <BellOutlined />}
                </span>
                <span className="notif-text">
                  <span className="notif-title">{n.title}</span>
                  {n.body && <span className="notif-body">{n.body}</span>}
                  <span className="notif-time">{timeAgo(n.createdAt)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )

  return (
    <Popover content={content} trigger="click" open={open} onOpenChange={toggle} placement="bottomRight" arrow={false}>
      <Button
        type="text"
        className="notif-bell"
        aria-label={unread > 0 ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'}
        icon={
          <Badge count={unread} size="small" overflowCount={99}>
            <BellOutlined style={{ fontSize: 20 }} />
          </Badge>
        }
      />
    </Popover>
  )
}
