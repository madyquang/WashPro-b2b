import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Col, Drawer, Dropdown, Form, Input, InputNumber, Modal, Row, Space, Table, Tag, Tooltip, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import {
  CheckCircleOutlined,
  DollarOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import KpiCard from '../components/KpiCard'
import { AgingCard, RevenueChart } from './finance/Charts'
import StatementModal from './finance/StatementModal'
import { getSession } from '../utils/auth'
import { formatCurrency, formatDate, getErrorMessage, moneyFormatter, moneyParser, todayIso } from '../utils/format'
import { paymentTermLabel, type DebtRow, type MonthRevenue, type Payment } from '../types'

interface PaymentForm {
  amount: number
  paidDate: string
  note?: string
}

export default function Finance() {
  const role = getSession()?.role
  const canRecord = role === 'WORKSHOP_ADMIN' || role === 'WORKSHOP_ACCOUNTANT'

  const [rows, setRows] = useState<DebtRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState<string | null>(null)

  const [payFor, setPayFor] = useState<DebtRow | null>(null)
  const [saving, setSaving] = useState(false)
  const [historyFor, setHistoryFor] = useState<DebtRow | null>(null)
  const [history, setHistory] = useState<Payment[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [form] = Form.useForm<PaymentForm>()
  const [revenue, setRevenue] = useState<MonthRevenue[]>([])
  const [voidTarget, setVoidTarget] = useState<Payment | null>(null)
  const [voidReason, setVoidReason] = useState('')
  const [voiding, setVoiding] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [debts, monthly] = await Promise.all([
        axiosClient.get<DebtRow[]>('/reports/debts'),
        axiosClient.get<MonthRevenue[]>('/reports/revenue', { params: { months: 6 } }),
      ])
      setRows(debts.data)
      setRevenue(monthly.data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được dữ liệu công nợ'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const totalBilled = rows.reduce((s, r) => s + r.billed, 0)
  const totalPaid = rows.reduce((s, r) => s + r.paid, 0)
  const totalBalance = totalBilled - totalPaid
  const totalOverdue = rows.reduce((s, r) => s + r.overdueTotal, 0)
  const [statementFor, setStatementFor] = useState<DebtRow | null>(null)

  const openHistory = async (r: DebtRow) => {
    setHistoryFor(r)
    setHistory([])
    setHistoryLoading(true)
    try {
      const { data } = await axiosClient.get<Payment[]>('/payments', { params: { restaurantId: r.restaurantId } })
      setHistory(data)
    } catch (e) {
      message.error(getErrorMessage(e, 'Không tải được lịch sử thanh toán'))
    } finally {
      setHistoryLoading(false)
    }
  }

  const confirmVoid = async () => {
    if (!voidTarget || !historyFor) return
    setVoiding(true)
    try {
      await axiosClient.patch(`/payments/${voidTarget.id}/void`, { reason: voidReason.trim() })
      message.success('Đã hủy khoản thanh toán')
      setVoidTarget(null)
      setVoidReason('')
      await Promise.all([openHistory(historyFor), load()])
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setVoiding(false)
    }
  }

  const pay = async (values: PaymentForm) => {
    if (!payFor) return
    setSaving(true)
    try {
      await axiosClient.post('/payments', {
        restaurantId: payFor.restaurantId,
        amount: values.amount,
        paidDate: values.paidDate,
        note: values.note?.trim() || null,
      })
      message.success('Đã ghi nhận thanh toán')
      setPayFor(null)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const download = async (path: string, filename: string) => {
    setExporting(path)
    try {
      const res = await axiosClient.get<Blob>(path, { responseType: 'blob' })
      const url = URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = url
      a.download = `${filename}-${todayIso()}.csv`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      message.error(getErrorMessage(e, 'Không xuất được file'))
    } finally {
      setExporting(null)
    }
  }

  const columns: TableColumnsType<DebtRow> = [
    {
      title: 'Khách hàng',
      dataIndex: 'restaurantName',
      render: (name: string, r) => (
        <>
          {name} {!r.active && <Tag>Ngừng hợp tác</Tag>}
        </>
      ),
    },
    {
      title: 'Còn nợ',
      dataIndex: 'balance',
      className: 'nowrap',
      align: 'right',
      sorter: (a, b) => a.balance - b.balance,
      render: (v: number) => <Typography.Text type={v > 0 ? undefined : 'success'} strong>{formatCurrency(v)}</Typography.Text>,
    },
    {
      title: 'Quá hạn',
      dataIndex: 'overdueTotal',
      className: 'nowrap',
      align: 'right',
      defaultSortOrder: 'descend',
      sorter: (a, b) => a.overdueTotal - b.overdueTotal,
      render: (v: number, r) =>
        v > 0 ? (
          <Tooltip
            title={
              <div>
                <div>Chưa đến hạn: {formatCurrency(r.notDue)}</div>
                <div>Quá 1–30 ngày: {formatCurrency(r.overdue1to30)}</div>
                <div>Quá 31–60 ngày: {formatCurrency(r.overdue31to60)}</div>
                <div>Quá trên 60 ngày: {formatCurrency(r.overdueOver60)}</div>
                {r.oldestUnpaidDue && <div>Hạn sớm nhất còn nợ: {formatDate(r.oldestUnpaidDue)}</div>}
              </div>
            }
          >
            <div style={{ cursor: 'help' }}>
              <Typography.Text type="danger" strong>
                {formatCurrency(v)}
              </Typography.Text>
              {r.overdueOver60 > 0 && (
                <div>
                  <Tag color="red" style={{ marginInlineEnd: 0, marginTop: 2 }}>
                    có nợ &gt;60 ngày
                  </Tag>
                </div>
              )}
            </div>
          </Tooltip>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
    { title: 'Kỳ thanh toán', dataIndex: 'paymentTermDays', className: 'nowrap', responsive: ['lg'], render: (d: number) => paymentTermLabel(d) },
    { title: 'Đơn HT', dataIndex: 'completedOrders', align: 'right', responsive: ['xxl'] },
    { title: 'Tổng tiền', dataIndex: 'billed', align: 'right', className: 'nowrap', responsive: ['xl'], render: (v: number) => formatCurrency(v) },
    { title: 'Đã thanh toán', dataIndex: 'paid', align: 'right', className: 'nowrap', responsive: ['md'], render: (v: number) => formatCurrency(v) },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      width: 230,
      render: (_: unknown, r) => (
        <Space size={4}>
          <Button size="small" onClick={() => setStatementFor(r)}>
            Sao kê
          </Button>
          <Button size="small" onClick={() => openHistory(r)}>
            Lịch sử
          </Button>
          {canRecord && (
            <Button
              size="small"
              type="primary"
              disabled={r.balance <= 0}
              onClick={() => {
                form.resetFields()
                form.setFieldsValue({ amount: r.balance, paidDate: todayIso() })
                setPayFor(r)
              }}
            >
              Thu tiền
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <div className="page-toolbar">
        <Typography.Title level={3} style={{ margin: 0 }}>
          Công nợ & thanh toán
        </Typography.Title>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
            Làm mới
          </Button>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'debts', label: 'Công nợ theo khách (CSV)', onClick: () => download('/reports/debts/export', 'cong-no') },
                { key: 'orders', label: 'Danh sách đơn (CSV)', onClick: () => download('/reports/orders/export', 'don-hang') },
                { key: 'items', label: 'Chi tiết đồ giặt (CSV)', onClick: () => download('/reports/items/export', 'chi-tiet-do-giat') },
              ],
            }}
          >
            <Button type="primary" icon={<DownloadOutlined />} loading={exporting !== null}>
              Xuất báo cáo
            </Button>
          </Dropdown>
        </Space>
      </div>

      {error && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={error} action={<Button size="small" onClick={load}>Thử lại</Button>} />}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <KpiCard icon={<DollarOutlined />} tone="navy" label="Tổng tiền đơn hoàn thành" value={formatCurrency(totalBilled)} />
        </Col>
        <Col xs={12} lg={6}>
          <KpiCard icon={<CheckCircleOutlined />} tone="green" label="Đã thanh toán" value={formatCurrency(totalPaid)} />
        </Col>
        <Col xs={12} lg={6}>
          <KpiCard icon={<WalletOutlined />} tone="teal" label="Còn nợ" value={formatCurrency(totalBalance)} />
        </Col>
        <Col xs={12} lg={6}>
          <KpiCard
            icon={<WarningOutlined />}
            tone={totalOverdue > 0 ? 'red' : 'gray'}
            label="Nợ quá hạn"
            value={formatCurrency(totalOverdue)}
            valueColor={totalOverdue > 0 ? '#C2341B' : undefined}
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={14}>
          <RevenueChart months={revenue} />
        </Col>
        <Col xs={24} lg={10}>
          <AgingCard rows={rows} />
        </Col>
      </Row>

      <Card
        title="Công nợ theo khách hàng"
        styles={{ body: { padding: 0 } }}
        extra={
          <Tooltip title="Hạn thanh toán mỗi đơn = ngày hoàn thành + kỳ thanh toán của khách. Tiền trả được trừ vào đơn cũ nhất trước. Khách quá hạn nhiều nhất xếp đầu.">
            <Typography.Text type="secondary" style={{ cursor: 'help' }}>
              <InfoCircleOutlined /> Cách tính
            </Typography.Text>
          </Tooltip>
        }
      >
        <Table<DebtRow>
          rowKey="restaurantId"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1000 }}
          pagination={{ pageSize: 10, showSizeChanger: false, style: { paddingInline: 16 } }}
          locale={{ emptyText: 'Chưa có dữ liệu công nợ' }}
        />
      </Card>

      <Modal
        title={payFor ? `Ghi nhận thanh toán · ${payFor.restaurantName}` : ''}
        open={payFor !== null}
        onCancel={() => setPayFor(null)}
        onOk={() => form.submit()}
        okText="Ghi nhận"
        cancelText="Hủy"
        confirmLoading={saving}
        forceRender
      >
        {payFor && <Alert type="info" showIcon style={{ marginBottom: 16 }} title={`Công nợ hiện tại: ${formatCurrency(payFor.balance)}`} />}
        <Form<PaymentForm> form={form} layout="vertical" onFinish={pay}>
          <Form.Item
            name="amount"
            label="Số tiền (đ)"
            rules={[
              { required: true, message: 'Vui lòng nhập số tiền' },
              { type: 'number', min: 1, message: 'Số tiền phải lớn hơn 0' },
              { type: 'number', max: payFor?.balance ?? Number.MAX_SAFE_INTEGER, message: 'Không được vượt quá công nợ hiện tại' },
            ]}
          >
            <InputNumber style={{ width: '100%' }} precision={0} min={0} formatter={moneyFormatter} parser={moneyParser} />
          </Form.Item>
          <Form.Item name="paidDate" label="Ngày thanh toán" rules={[{ required: true, message: 'Vui lòng chọn ngày' }]}>
            <Input type="date" max={todayIso()} />
          </Form.Item>
          <Form.Item name="note" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={500} placeholder="Chuyển khoản, tiền mặt, mã giao dịch..." />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title={historyFor ? `Lịch sử thanh toán · ${historyFor.restaurantName}` : ''} open={historyFor !== null} onClose={() => setHistoryFor(null)} size={Math.min(520, window.innerWidth)} destroyOnHidden>
        <Table<Payment>
          rowKey="id"
          size="small"
          loading={historyLoading}
          dataSource={history}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          locale={{ emptyText: 'Chưa có khoản thanh toán nào' }}
          scroll={{ x: 'max-content' }}
          columns={[
            { title: 'Ngày', dataIndex: 'paidDate', render: (v: string) => formatDate(v) },
            {
              title: 'Số tiền',
              dataIndex: 'amount',
              align: 'right',
              render: (v: number, p) => (
                <Typography.Text delete={p.voided} type={p.voided ? 'secondary' : undefined}>
                  {formatCurrency(v)}
                </Typography.Text>
              ),
            },
            { title: 'Ghi nhận bởi', dataIndex: 'createdBy' },
            {
              title: 'Ghi chú',
              dataIndex: 'note',
              render: (v: string | null, p) =>
                p.voided ? <Tag color="red">Đã hủy: {p.voidReason} ({p.voidedBy})</Tag> : v || '—',
            },
            {
              title: '',
              key: 'void',
              render: (_: unknown, p) =>
                canRecord && !p.voided ? (
                  <Button size="small" danger type="text" onClick={() => setVoidTarget(p)}>
                    Hủy
                  </Button>
                ) : null,
            },
          ]}
        />
      </Drawer>

      <StatementModal customer={statementFor} onClose={() => setStatementFor(null)} />

      <Modal
        title="Hủy khoản thanh toán?"
        open={voidTarget !== null}
        onCancel={() => setVoidTarget(null)}
        onOk={confirmVoid}
        okText="Hủy khoản này"
        cancelText="Đóng"
        okButtonProps={{ danger: true, disabled: voidReason.trim() === '' }}
        confirmLoading={voiding}
        destroyOnHidden
      >
        <Typography.Paragraph>
          Khoản {voidTarget ? formatCurrency(voidTarget.amount) : ''} sẽ không còn được tính vào công nợ nhưng vẫn lưu lại để đối soát.
        </Typography.Paragraph>
        <Input.TextArea rows={3} maxLength={500} showCount placeholder="Lý do hủy (bắt buộc)" value={voidReason} onChange={(e) => setVoidReason(e.target.value)} />
      </Modal>
    </>
  )
}
