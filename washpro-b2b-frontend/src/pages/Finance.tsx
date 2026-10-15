import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Col, Drawer, Dropdown, Form, Grid, Input, InputNumber, Modal, Row, Space, Table, Tooltip, Typography } from 'antd'
import { message } from '../utils/message'
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
import LoadError from '../components/LoadError'
import PageHeader from '../components/PageHeader'
import ResponsiveTable from '../components/ResponsiveTable'
import StatStrip from '../components/StatStrip'
import ToneTag from '../components/ToneTag'
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
  const screens = Grid.useBreakpoint()

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
          {name} {!r.active && <ToneTag tone="gray">Ngừng hợp tác</ToneTag>}
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
                  <ToneTag tone="red">có nợ &gt;60 ngày</ToneTag>
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

  // "Cách tính" là nút (bấm / Tab được) thay vì chữ chỉ có hover
  const howItWorks = (
    <Tooltip title="Hạn thanh toán mỗi đơn = ngày hoàn thành + kỳ thanh toán của khách. Tiền trả được trừ vào đơn cũ nhất trước. Khách quá hạn nhiều nhất xếp đầu.">
      <Button type="link" size="small" icon={<InfoCircleOutlined />} style={{ paddingInline: 0 }}>
        Cách tính
      </Button>
    </Tooltip>
  )

  const debtTable = (
    <ResponsiveTable<DebtRow>
      table={{
        rowKey: 'restaurantId',
        loading,
        columns,
        dataSource: rows,
        scroll: { x: 1000 },
        pagination: { pageSize: 10, showSizeChanger: false, style: { paddingInline: 16 } },
      }}
      cardTitle={(r) => r.restaurantName}
      hideInCard={['restaurantName']}
      emptyText="Chưa có dữ liệu công nợ"
    />
  )

  return (
    <>
      <PageHeader
        title="Công nợ & thanh toán"
        description={
          canRecord
            ? 'Công nợ từng khách hàng, ghi nhận thanh toán, sao kê tháng và xuất báo cáo.'
            : 'Công nợ của bạn với xưởng, sao kê tháng và lịch sử thanh toán.'
        }
        actions={
          <>
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
          </>
        }
      />

      <LoadError error={error} onRetry={load} />

      <div className="mb-4">
        <StatStrip
          aria-label="Tổng hợp công nợ"
          items={[
            { key: 'billed', icon: <DollarOutlined />, tone: 'navy', label: 'Tổng tiền đơn hoàn thành', value: formatCurrency(totalBilled) },
            { key: 'paid', icon: <CheckCircleOutlined />, tone: 'green', label: 'Đã thanh toán', value: formatCurrency(totalPaid) },
            { key: 'balance', icon: <WalletOutlined />, tone: 'teal', label: 'Còn nợ', value: formatCurrency(totalBalance) },
            {
              key: 'overdue',
              icon: <WarningOutlined />,
              tone: totalOverdue > 0 ? 'red' : 'gray',
              emphasize: totalOverdue > 0,
              label: 'Nợ quá hạn',
              value: formatCurrency(totalOverdue),
            },
          ]}
        />
      </div>

      <Row gutter={[16, 16]} className="mb-4">
        <Col xs={24} lg={14}>
          <RevenueChart months={revenue} />
        </Col>
        <Col xs={24} lg={10}>
          <AgingCard rows={rows} />
        </Col>
      </Row>

      {screens.md !== false ? (
        <Card title="Công nợ theo khách hàng" styles={{ body: { padding: 0 } }} extra={howItWorks}>
          {debtTable}
        </Card>
      ) : (
        // Điện thoại: mỗi khách một thẻ (không lồng thẻ trong thẻ, không kéo ngang)
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
            <h2 className="section-title">Công nợ theo khách hàng</h2>
            {howItWorks}
          </div>
          {debtTable}
        </>
      )}

      <Modal
        title={payFor ? `Ghi nhận thanh toán · ${payFor.restaurantName}` : ''}
        open={payFor !== null}
        onCancel={() => setPayFor(null)}
        onOk={() => form.submit()}
        okText="Ghi nhận"
        cancelText="Hủy"
        confirmLoading={saving}
        forceRender
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
      >
        {payFor && <Alert type="info" showIcon className="mb-4" title={`Công nợ hiện tại: ${formatCurrency(payFor.balance)}`} />}
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

      <Drawer title={historyFor ? `Lịch sử thanh toán · ${historyFor.restaurantName}` : ''} open={historyFor !== null} onClose={() => setHistoryFor(null)} size={Math.min(760, window.innerWidth)} destroyOnHidden>
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
              width: 240,
              render: (v: string | null, p) =>
                p.voided ? <ToneTag tone="red">Đã hủy: {p.voidReason} ({p.voidedBy})</ToneTag> : v || '—',
            },
            {
              // Cố định bên phải: nút hủy luôn nhìn thấy, không bị đẩy ra ngoài khi bảng rộng hơn drawer
              title: 'Thao tác',
              key: 'void',
              fixed: 'right',
              width: 84,
              render: (_: unknown, p) =>
                canRecord && !p.voided ? (
                  <Button size="small" danger type="text" aria-label={`Hủy khoản thanh toán ngày ${formatDate(p.paidDate)}`} onClick={() => setVoidTarget(p)}>
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
        <Input.TextArea rows={3} maxLength={500} showCount aria-label="Lý do hủy khoản thanh toán" placeholder="Lý do hủy (bắt buộc)" value={voidReason} onChange={(e) => setVoidReason(e.target.value)} />
      </Modal>
    </>
  )
}
