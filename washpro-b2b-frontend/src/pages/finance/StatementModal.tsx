import { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Divider, Empty, Input, Modal, Skeleton, Space, Table, Typography, message } from 'antd'
import { PrinterOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import { currentMonth, formatCurrency, formatDate, formatDateTime, getErrorMessage } from '../../utils/format'
import { printStatement } from '../../utils/print'
import { CUSTOMER_TYPE_LABELS, paymentTermLabel, type Statement } from '../../types'

interface Props {
  /** Khách cần xem sao kê; null = đóng. */
  customer: { restaurantId: number; restaurantName: string } | null
  onClose: () => void
}

/** Sao kê công nợ theo tháng: nợ đầu kỳ + phát sinh − đã thanh toán = nợ cuối kỳ. */
export default function StatementModal({ customer, onClose }: Props) {
  const [month, setMonth] = useState(currentMonth())
  const [data, setData] = useState<Statement | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!customer || !month) return
    setLoading(true)
    setError(null)
    axiosClient
      .get<Statement>('/reports/statement', { params: { restaurantId: customer.restaurantId, month } })
      .then((res) => setData(res.data))
      .catch((e) => setError(getErrorMessage(e, 'Không tải được sao kê')))
      .finally(() => setLoading(false))
  }, [customer, month])

  const [y, m] = month.split('-')

  return (
    <Modal
      title={customer ? `Sao kê công nợ · ${customer.restaurantName}` : ''}
      open={customer !== null}
      onCancel={onClose}
      width={{ xs: '100%', md: 760 }}
      className="sheet-modal"
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose}>Đóng</Button>
          <Button
            type="primary"
            icon={<PrinterOutlined />}
            disabled={!data}
            onClick={() => {
              if (data && !printStatement(data)) message.warning('Trình duyệt đã chặn cửa sổ in, hãy cho phép cửa sổ bật lên')
            }}
          >
            In sao kê
          </Button>
        </Space>
      }
    >
      <Space className="mb-4">
        <span>Tháng:</span>
        <Input type="month" value={month} max={currentMonth()} onChange={(e) => setMonth(e.target.value)} style={{ width: 180 }} />
      </Space>

      {error && <Alert type="error" showIcon title={error} />}
      {loading && <Skeleton active paragraph={{ rows: 6 }} />}
      {!loading && data && (
        <>
          <Descriptions size="small" column={{ xs: 1, sm: 2 }} bordered>
            <Descriptions.Item label="Loại khách">{CUSTOMER_TYPE_LABELS[data.customerType]}</Descriptions.Item>
            <Descriptions.Item label="Kỳ thanh toán">{paymentTermLabel(data.paymentTermDays)}</Descriptions.Item>
            <Descriptions.Item label={`Nợ đầu kỳ (01/${m}/${y})`}>{formatCurrency(data.openingBalance)}</Descriptions.Item>
            <Descriptions.Item label="Phát sinh trong tháng">{formatCurrency(data.totalBilled)}</Descriptions.Item>
            <Descriptions.Item label="Đã thanh toán trong tháng">{formatCurrency(data.totalPaid)}</Descriptions.Item>
            <Descriptions.Item label="Nợ cuối kỳ">
              <Typography.Text strong type={data.closingBalance > 0 ? 'danger' : 'success'}>
                {formatCurrency(data.closingBalance)}
              </Typography.Text>
            </Descriptions.Item>
          </Descriptions>

          <Divider titlePlacement="start">Đơn hoàn thành trong tháng ({data.orders.length})</Divider>
          {data.orders.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có đơn hoàn thành" />
          ) : (
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={data.orders}
              scroll={{ x: 'max-content' }}
              columns={[
                { title: 'Mã đơn', dataIndex: 'id', render: (v: number) => `#${v}` },
                { title: 'Hoàn thành', dataIndex: 'completedAt', render: (v: string) => formatDateTime(v) },
                { title: 'Hạn thanh toán', dataIndex: 'paymentDue', render: (v: string) => formatDate(v) },
                { title: 'Số tiền', dataIndex: 'amount', align: 'right', render: (v: number) => formatCurrency(v) },
              ]}
            />
          )}

          <Divider titlePlacement="start">Thanh toán trong tháng ({data.payments.length})</Divider>
          {data.payments.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có khoản thanh toán" />
          ) : (
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={data.payments}
              scroll={{ x: 'max-content' }}
              columns={[
                { title: 'Ngày', dataIndex: 'paidDate', render: (v: string) => formatDate(v) },
                { title: 'Số tiền', dataIndex: 'amount', align: 'right', render: (v: number) => formatCurrency(v) },
                { title: 'Ghi chú', dataIndex: 'note', render: (v: string | null) => v || '—' },
              ]}
            />
          )}
        </>
      )}
    </Modal>
  )
}
