import { useEffect, useState } from 'react'
import { Card, Input, Segmented, Space, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { CarOutlined, ClockCircleOutlined, ExclamationCircleOutlined, FieldTimeOutlined, FileTextOutlined, InboxOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import PageHeader from '../components/PageHeader'
import ResponsiveTable from '../components/ResponsiveTable'
import StatStrip, { type Stat } from '../components/StatStrip'
import ToneTag from '../components/ToneTag'
import type { StatusTone } from '../theme'
import { getSession } from '../utils/auth'
import { formatCurrency, getErrorMessage, todayIso } from '../utils/format'
import type { PerformanceCustomerRow, PerformanceDriverRow, PerformanceReport } from '../types'

type Preset = 'month' | 'lastMonth' | 'days30' | 'custom'

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

function rangeOf(preset: Preset): [string, string] {
  const now = new Date()
  if (preset === 'lastMonth') {
    return [iso(new Date(now.getFullYear(), now.getMonth() - 1, 1)), iso(new Date(now.getFullYear(), now.getMonth(), 0))]
  }
  if (preset === 'days30') return [iso(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29)), iso(now)]
  return [iso(new Date(now.getFullYear(), now.getMonth(), 1)), iso(now)]
}

const pct = (r: number | null) => (r === null ? '—' : `${Math.round(r * 1000) / 10}%`)
const hours = (h: number | null) => (h === null ? '—' : h >= 48 ? `${Math.round((h / 24) * 10) / 10} ngày` : `${h} giờ`)

/** Đúng hạn: ≥ 90% tốt, ≥ 75% cần chú ý, thấp hơn là đáng lo. */
const onTimeTone = (r: number | null): StatusTone => (r === null ? 'gray' : r >= 0.9 ? 'green' : r >= 0.75 ? 'gold' : 'red')
/** Sai lệch / sự cố: ≤ 5% tốt, ≤ 15% cần chú ý. */
const problemTone = (r: number | null): StatusTone => (r === null ? 'gray' : r <= 0.05 ? 'green' : r <= 0.15 ? 'gold' : 'red')
const rateTag = (tone: (r: number | null) => StatusTone) => (v: number | null) => <ToneTag tone={tone(v)}>{pct(v)}</ToneTag>

/** Báo cáo hiệu suất (xưởng) / chất lượng dịch vụ (quản lý phía khách, chỉ số của khách mình). */
export default function Performance() {
  const role = getSession()?.role
  const isCustomer = role === 'CUSTOMER_MANAGER'
  const [preset, setPreset] = useState<Preset>('month')
  const [custom, setCustom] = useState<[string, string]>(rangeOf('month'))
  const [from, to] = preset === 'custom' ? custom : rangeOf(preset)
  const [report, setReport] = useState<PerformanceReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!from || !to) return
    // Đổi khoảng thời gian khi yêu cầu trước chưa xong: bỏ kết quả cũ
    let ignore = false
    setError(null)
    axiosClient
      .get<PerformanceReport>('/reports/performance', { params: { from, to } })
      .then(({ data }) => !ignore && setReport(data))
      .catch((e) => !ignore && setError(getErrorMessage(e, 'Không tải được báo cáo')))
    return () => {
      ignore = true
    }
  }, [from, to, reloadKey])

  const s = report?.summary
  const stats: Stat[] = s
    ? [
        { key: 'orders', label: 'Đơn nhận', value: s.orders, hint: `${s.bags} túi · ${formatCurrency(s.orderValue)}`, icon: <FileTextOutlined /> },
        {
          key: 'ontime',
          label: 'Giao đúng hạn',
          value: pct(s.onTimeRate),
          hint: s.delivered ? `${s.onTime}/${s.delivered} đơn đã giao` : 'Chưa có đơn giao trả',
          icon: <ClockCircleOutlined />,
          tone: onTimeTone(s.onTimeRate),
          emphasize: s.onTimeRate !== null && s.onTimeRate < 0.9,
        },
        { key: 'turnaround', label: 'Thời gian xử lý TB', value: hours(s.avgTurnaroundHours), hint: 'Từ lúc nhận đồ tới lúc giao trả', icon: <FieldTimeOutlined /> },
        {
          key: 'discrepancy',
          label: 'Sai lệch số lượng',
          value: pct(s.discrepancyRate),
          hint: `${s.discrepancyOrders} đơn · sự cố khi nhận ${pct(s.issueRate)}`,
          icon: <ExclamationCircleOutlined />,
          tone: problemTone(s.discrepancyRate),
          emphasize: (s.discrepancyRate ?? 0) > 0.05,
        },
        {
          key: 'pickups',
          label: 'Yêu cầu lấy đồ',
          value: `${s.pickupsDone}/${s.pickupsRequested}`,
          hint: `đã lấy · ${s.pickupsCancelled} hủy · chờ TB ${hours(s.avgPickupHours)}`,
          icon: <InboxOutlined />,
        },
      ]
    : []

  const customerColumns: TableColumnsType<PerformanceCustomerRow> = [
    { title: 'Khách hàng', dataIndex: 'customerName', key: 'customerName' },
    { title: 'Đơn', dataIndex: 'orders', key: 'orders', align: 'right' },
    { title: 'Túi', dataIndex: 'bags', key: 'bags', align: 'right' },
    { title: 'Giá trị', dataIndex: 'orderValue', key: 'orderValue', align: 'right', render: (v: number) => formatCurrency(v) },
    { title: 'Đúng hạn', dataIndex: 'onTimeRate', key: 'onTimeRate', align: 'right', render: rateTag(onTimeTone) },
    { title: 'Xử lý TB', dataIndex: 'avgTurnaroundHours', key: 'avgTurnaroundHours', align: 'right', render: hours },
    { title: 'Sai lệch', dataIndex: 'discrepancyRate', key: 'discrepancyRate', align: 'right', render: rateTag(problemTone) },
    { title: 'Sự cố nhận', dataIndex: 'issueRate', key: 'issueRate', align: 'right', render: rateTag(problemTone) },
  ]

  const driverColumns: TableColumnsType<PerformanceDriverRow> = [
    { title: 'Tài xế', dataIndex: 'driverName', key: 'driverName' },
    { title: 'Đơn tạo', dataIndex: 'orders', key: 'orders', align: 'right' },
    { title: 'Túi', dataIndex: 'bags', key: 'bags', align: 'right' },
    { title: 'Lấy theo yêu cầu', dataIndex: 'pickupsDone', key: 'pickupsDone', align: 'right' },
    { title: 'Chờ lấy TB', dataIndex: 'avgPickupHours', key: 'avgPickupHours', align: 'right', render: hours },
    { title: 'Sai lệch', dataIndex: 'discrepancyRate', key: 'discrepancyRate', align: 'right', render: rateTag(problemTone) },
  ]

  return (
    <div>
      <PageHeader
        title={isCustomer ? 'Chất lượng dịch vụ' : 'Báo cáo hiệu suất'}
        description={
          isCustomer
            ? 'Tỉ lệ giao đúng hạn, thời gian xử lý và sai lệch của các đơn của bạn.'
            : 'Đúng hạn, thời gian xử lý, sai lệch theo khách hàng và tài xế. Tính trên các đơn nhận trong khoảng thời gian chọn.'
        }
      />

      <Space wrap className="mb-4" align="center">
        <Segmented<Preset>
          value={preset}
          onChange={setPreset}
          options={[
            { value: 'month', label: 'Tháng này' },
            { value: 'lastMonth', label: 'Tháng trước' },
            { value: 'days30', label: '30 ngày qua' },
            { value: 'custom', label: 'Tùy chọn' },
          ]}
        />
        {preset === 'custom' && (
          <Space wrap>
            <Input type="date" aria-label="Từ ngày" value={custom[0]} max={custom[1] || todayIso()} onChange={(e) => setCustom([e.target.value, custom[1]])} />
            <Input type="date" aria-label="Đến ngày" value={custom[1]} min={custom[0]} max={todayIso()} onChange={(e) => setCustom([custom[0], e.target.value])} />
          </Space>
        )}
      </Space>

      <LoadError error={error} onRetry={() => setReloadKey((k) => k + 1)} />
      {report && !error && (
        <>
          <StatStrip items={stats} aria-label="Chỉ số hiệu suất" />

          {/* Quản lý phía khách chỉ có một dòng (khách mình), trùng phần chỉ số ở trên */}
          {!isCustomer && (
            <Card title="Theo khách hàng" className="mb-4" style={{ marginTop: 16 }}>
              <ResponsiveTable<PerformanceCustomerRow>
                table={{ rowKey: 'customerId', columns: customerColumns, dataSource: report.customers, pagination: false, size: 'small', scroll: { x: 'max-content' } }}
                cardTitle={(r) => r.customerName}
                cardExtra={(r) => <ToneTag tone={onTimeTone(r.onTimeRate)}>Đúng hạn {pct(r.onTimeRate)}</ToneTag>}
                hideInCard={['customerName', 'onTimeRate']}
                emptyText="Không có đơn nào trong khoảng thời gian này."
              />
            </Card>
          )}

          {report.drivers && (
            <Card
              title={
                <>
                  <CarOutlined /> Theo tài xế
                </>
              }
            >
              <ResponsiveTable<PerformanceDriverRow>
                table={{ rowKey: 'driverId', columns: driverColumns, dataSource: report.drivers, pagination: false, size: 'small', scroll: { x: 'max-content' } }}
                cardTitle={(r) => r.driverName}
                hideInCard={['driverName']}
                emptyText="Không có đơn nào trong khoảng thời gian này."
              />
              <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
                "Chờ lấy TB": thời gian trung bình từ lúc khách gửi yêu cầu tới lúc tài xế lấy đồ (tạo đơn).
              </Typography.Paragraph>
            </Card>
          )}
        </>
      )}
    </div>
  )
}
