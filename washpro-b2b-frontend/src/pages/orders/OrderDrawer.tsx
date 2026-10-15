import { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Divider, Drawer, Empty, Skeleton, Table, Timeline, Typography, message } from 'antd'
import { PrinterOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import CategoryTag from '../../components/CategoryTag'
import DueTag from '../../components/DueTag'
import StatusTag from '../../components/StatusTag'
import { catalogLabel, formatQuantity, groupByCategory } from '../../utils/catalog'
import { formatCurrency, formatDateTime, getErrorMessage } from '../../utils/format'
import { getSession } from '../../utils/auth'
import { printDeliverySlip } from '../../utils/print'
import { CUSTOMER_TYPE_LABELS, STATUS_META, type Order, type OrderItem } from '../../types'

interface Props {
  orderId: number | null
  onClose: () => void
}

export default function OrderDrawer({ orderId, onClose }: Props) {
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const role = getSession()?.role
  // Tài xế và nhân viên phía khách không cần thấy tiền
  const canSeeMoney = role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT' || role === 'RESTAURANT_MANAGER'

  useEffect(() => {
    if (orderId === null) return
    // Chuyển nhanh sang đơn khác: bỏ phản hồi của đơn cũ đến muộn (tránh hiện đơn A dưới tiêu đề đơn B)
    let ignore = false
    setLoading(true)
    setError(null)
    setOrder(null)
    axiosClient
      .get<Order>(`/orders/${orderId}`)
      .then((res) => !ignore && setOrder(res.data))
      .catch((e) => !ignore && setError(getErrorMessage(e, 'Không tải được chi tiết đơn')))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [orderId])

  return (
    <Drawer
      title={orderId !== null ? `Đơn hàng #${orderId}` : ''}
      open={orderId !== null}
      onClose={onClose}
      size={Math.min(640, window.innerWidth)}
      extra={
        order && (
          <Button
            icon={<PrinterOutlined />}
            onClick={() => {
              if (!printDeliverySlip(order)) message.warning('Trình duyệt đã chặn cửa sổ in, hãy cho phép cửa sổ bật lên')
            }}
          >
            In phiếu
          </Button>
        )
      }
      destroyOnHidden
    >
      {loading && <Skeleton active paragraph={{ rows: 8 }} />}
      {error && <Alert type="error" showIcon title={error} />}
      {order && (
        <>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Khách hàng">
              {order.restaurantName} <Typography.Text type="secondary">({CUSTOMER_TYPE_LABELS[order.customerType]})</Typography.Text>
            </Descriptions.Item>
            {order.restaurantAddress && <Descriptions.Item label="Địa chỉ">{order.restaurantAddress}</Descriptions.Item>}
            {order.restaurantPhone && (
              <Descriptions.Item label="Liên hệ">
                <a href={`tel:${order.restaurantPhone}`}>{order.restaurantPhone}</a>
                {order.contactName && ` · ${order.contactName}`}
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Tài xế">{order.driverName}</Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <StatusTag status={order.status} />
            </Descriptions.Item>
            <Descriptions.Item label="Số túi / bao">{order.totalBags}</Descriptions.Item>
            <Descriptions.Item label="Ngày nhận đồ">{formatDateTime(order.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="Hạn trả">
              {order.status === 'COMPLETED' ? (
                formatDateTime(order.dueAt)
              ) : (
                <>
                  {formatDateTime(order.dueAt)} <DueTag order={order} onlyUrgent />
                </>
              )}
            </Descriptions.Item>
            {order.completedAt && <Descriptions.Item label="Hoàn thành">{formatDateTime(order.completedAt)}</Descriptions.Item>}
            <Descriptions.Item label="Cập nhật cuối">{formatDateTime(order.updatedAt)}</Descriptions.Item>
            <Descriptions.Item label="Ghi chú">
              {order.description ? <span className="pre-wrap">{order.description}</span> : '—'}
            </Descriptions.Item>
          </Descriptions>

          <Divider titlePlacement="start">Chi tiết đồ giặt</Divider>
          {order.items.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có chi tiết đồ giặt" />
          ) : (
            <>
              {groupByCategory(order.items).map((g) => (
                <div key={g.category} className="mb-3">
                  <div style={{ marginBottom: 6 }}>
                    <CategoryTag category={g.category} />
                  </div>
                  <Table<OrderItem>
                    size="small"
                    pagination={false}
                    rowKey="id"
                    dataSource={g.items}
                    scroll={{ x: 'max-content' }}
                    columns={[
                      { title: 'Loại đồ', key: 'name', render: (_: unknown, i) => catalogLabel(i) },
                      { title: 'SL', key: 'qty', align: 'right', render: (_: unknown, i) => formatQuantity(i.quantity, i.unit) },
                      ...(canSeeMoney
                        ? [
                            { title: 'Đơn giá', dataIndex: 'unitPrice', align: 'right' as const, render: (v: number | null) => formatCurrency(v) },
                            { title: 'Thành tiền', dataIndex: 'lineTotal', align: 'right' as const, render: (v: number | null) => formatCurrency(v) },
                          ]
                        : []),
                    ]}
                  />
                </div>
              ))}
              {canSeeMoney && (
                <div style={{ textAlign: 'right', marginTop: 8 }}>
                  <Typography.Text strong>Tổng: {formatCurrency(order.totalAmount)}</Typography.Text>
                  {!order.billable && (
                    <div>
                      <Typography.Text type="warning">Còn dòng chưa có đơn giá</Typography.Text>
                    </div>
                  )}
                </div>
              )}
            </>
          )}

          <Divider titlePlacement="start">Lịch sử xử lý</Divider>
          <Timeline
            items={(order.history ?? []).map((h) => ({
              content: (
                <div>
                  <div>
                    <strong>{STATUS_META[h.toStatus].label}</strong> · {h.actor}
                  </div>
                  {h.note && <div className="pre-wrap">{h.note}</div>}
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {formatDateTime(h.createdAt)}
                  </Typography.Text>
                </div>
              ),
            }))}
          />
        </>
      )}
    </Drawer>
  )
}
