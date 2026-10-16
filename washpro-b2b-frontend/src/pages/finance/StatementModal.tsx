import { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Divider, Empty, Input, Modal, Skeleton, Space, Table, Typography } from 'antd'
import { message } from '../../utils/message'
import { FilePdfOutlined, PrinterOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import { currentMonth, formatCurrency, formatDate, formatDateTime, getErrorMessage } from '../../utils/format'
import { printStatement } from '../../utils/print'
import { CUSTOMER_TYPE_LABELS, paymentTermLabel, type PaymentQr, type Statement } from '../../types'

interface Props {
  /** Khách cần xem sao kê; null = đóng. */
  customer: { customerId: number; customerName: string } | null
  onClose: () => void
}

/** Sao kê công nợ theo tháng: nợ đầu kỳ + phát sinh − đã thanh toán = nợ cuối kỳ. */
export default function StatementModal({ customer, onClose }: Props) {
  const [month, setMonth] = useState(currentMonth())
  const [data, setData] = useState<Statement | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [qr, setQr] = useState<PaymentQr | null>(null)

  // Còn nợ cuối kỳ: lấy mã QR chuyển khoản (204 = chưa cấu hình ngân hàng / không còn nợ)
  useEffect(() => {
    if (!customer || !data || data.closingBalance <= 0) return
    let ignore = false
    axiosClient
      .get<PaymentQr | ''>('/reports/payment-qr', { params: { customerId: customer.customerId, month } })
      .then((res) => !ignore && setQr(res.status === 200 && res.data ? res.data : null))
      .catch(() => !ignore && setQr(null))
    return () => {
      ignore = true
      setQr(null)
    }
  }, [customer, data, month])

  useEffect(() => {
    if (!customer || !month) return
    // Xóa sao kê đang hiện (của khách / tháng trước) và bỏ phản hồi cũ đến muộn khi đổi khách hoặc đổi tháng nhanh
    let ignore = false
    setLoading(true)
    setError(null)
    setData(null)
    axiosClient
      .get<Statement>('/reports/statement', { params: { customerId: customer.customerId, month } })
      .then((res) => !ignore && setData(res.data))
      .catch((e) => !ignore && setError(getErrorMessage(e, 'Không tải được sao kê')))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [customer, month])

  const [y, m] = month.split('-')

  /** Hóa đơn tháng (PDF) do máy chủ dựng: cùng số liệu với sao kê, kèm chi tiết dịch vụ và thông tin chuyển khoản. */
  const downloadInvoice = async () => {
    if (!customer) return
    setDownloading(true)
    try {
      const res = await axiosClient.get<Blob>('/reports/invoice', { params: { customerId: customer.customerId, month }, responseType: 'blob' })
      const url = URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = url
      a.download = `hoa-don-${y}-${m}-${customer.customerName.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.pdf`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      message.error(getErrorMessage(e, 'Không tải được hóa đơn'))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <Modal
      title={customer ? `Sao kê công nợ · ${customer.customerName}` : ''}
      open={customer !== null}
      onCancel={onClose}
      width={{ xs: '100%', md: 760 }}
      className="sheet-modal"
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose}>Đóng</Button>
          <Button icon={<FilePdfOutlined />} disabled={!data} loading={downloading} onClick={downloadInvoice}>
            Hóa đơn PDF
          </Button>
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

          {qr && (
            <div className="payment-qr">
              <img src={qr.image} alt={`Mã QR chuyển khoản ${formatCurrency(qr.amount)}`} width={160} height={160} />
              <div>
                <Typography.Text strong>Quét mã bằng ứng dụng ngân hàng để thanh toán</Typography.Text>
                <div>Số tiền: {formatCurrency(qr.amount)}</div>
                <div>
                  Tài khoản: {qr.bankAccount}
                  {qr.bankName && ` · ${qr.bankName}`}
                  {qr.accountHolder && ` · ${qr.accountHolder}`}
                </div>
                <div>Nội dung: {qr.content}</div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Số tiền và nội dung được điền sẵn (chuẩn VietQR), kế toán đối chiếu theo nội dung chuyển khoản.
                </Typography.Text>
                <div style={{ marginTop: 6 }}>
                  <a href={qr.image} download={`qr-${qr.content}.png`}>
                    Tải ảnh mã QR
                  </a>
                </div>
              </div>
            </div>
          )}

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
