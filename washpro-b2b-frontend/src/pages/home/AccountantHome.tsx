import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button, Card, Grid, Skeleton } from 'antd'
import { CheckCircleOutlined, DollarOutlined, ReloadOutlined, WalletOutlined, WarningOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import EmptyState from '../../components/EmptyState'
import LoadError from '../../components/LoadError'
import PageHeader from '../../components/PageHeader'
import StatStrip, { type Stat } from '../../components/StatStrip'
import { OrderCard, OrderRow } from '../orders/OrderCard'
import { orderHint } from '../orders/hints'
import { useOrderActions } from '../orders/useOrderActions'
import { CardGrid, Section, StatusStrip } from './HomeParts'
import { greetingFor, todayLabel } from './greeting'
import { getSession } from '../../utils/auth'
import { formatCurrency, getErrorMessage } from '../../utils/format'
import type { Dashboard, Order, OrderPage } from '../../types'

/**
 * Trang chủ Kế toán: đơn còn thiếu đơn giá (xưởng chưa giao trả được) lên đầu với nút nhập giá ngay,
 * sau đó là tài chính. Đơn đang giặt phải đủ giá trước khi giao.
 */
export default function AccountantHome() {
  const session = getSession()
  const navigate = useNavigate()
  const screens = Grid.useBreakpoint()
  const [data, setData] = useState<Dashboard | null>(null)
  const [needPrice, setNeedPrice] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!getSession()) return // đã đăng xuất: không gửi request không có token
    setLoading(true)
    setError(null)
    try {
      const [d, o] = await Promise.all([
        axiosClient.get<Dashboard>('/dashboard'),
        // Máy chủ chưa lọc được theo "thiếu giá": lấy các đơn chưa hoàn thành (hạn trả gần trước) rồi lọc tại đây
        axiosClient.get<OrderPage>('/orders', { params: { status: 'RECEIVED,WASHING,WASHED,DELIVERY_ISSUE', sort: 'DUE', size: 100 } }),
      ])
      setData(d.data)
      setNeedPrice(o.data.content.filter((x) => !x.billable))
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được trang chủ'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const orderActions = useOrderActions(load)

  // Đã đăng xuất trong lúc trang còn đang tải (dữ liệu về muộn làm trang vẽ lại): không vẽ gì, tránh đọc phiên rỗng
  if (!session) return null
  const washingFirst = [...needPrice].sort((a, b) => Number(b.status === 'WASHING') - Number(a.status === 'WASHING'))

  const overdueDebt = data?.overdueDebt ?? 0
  const finance: Stat[] = data
    ? [
        { key: 'billed', icon: <DollarOutlined />, tone: 'navy', label: 'Tổng tiền đã chốt', value: formatCurrency(data.billed) },
        { key: 'paid', icon: <CheckCircleOutlined />, tone: 'green', label: 'Đã thanh toán', value: formatCurrency(data.paid) },
        { key: 'balance', icon: <WalletOutlined />, tone: 'teal', label: 'Công nợ còn lại', value: formatCurrency(data.balance), to: '/finance' },
        {
          key: 'overdueDebt',
          icon: <WarningOutlined />,
          tone: overdueDebt > 0 ? 'red' : 'gray',
          emphasize: overdueDebt > 0,
          label: 'Nợ quá hạn thanh toán',
          value: formatCurrency(data.overdueDebt),
          to: '/finance',
        },
      ]
    : []

  return (
    <>
      <PageHeader
        title={greetingFor(session.fullName)}
        description={`${todayLabel()} · Chốt giá đơn đang giặt và theo dõi công nợ khách hàng.`}
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading} aria-label="Làm mới" />
            <Button type="primary" icon={<WalletOutlined />} onClick={() => navigate('/finance')}>
              Công nợ & thanh toán
            </Button>
          </>
        }
      />
      <LoadError error={error} onRetry={load} />

      {!data ? (
        loading && <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <>
          <Section title="Đơn cần nhập đơn giá" count={needPrice.length} extra={<Link to="/orders?status=WASHING">Đơn đang giặt</Link>}>
            {needPrice.length === 0 ? (
              <EmptyState compact description="Mọi đơn đang xử lý đều đã đủ đơn giá." />
            ) : screens.md !== false ? (
              <Card styles={{ body: { padding: 0 } }}>
                {washingFirst.slice(0, 10).map((o) => (
                  <OrderRow
                    key={o.id}
                    order={o}
                    actions={orderActions.actionsFor(o)}
                    busy={orderActions.busyId === o.id}
                    onDetail={() => orderActions.openDetail(o.id)}
                    onMenuAction={orderActions.runFromMenu}
                  />
                ))}
              </Card>
            ) : (
              <CardGrid>
                {washingFirst.slice(0, 10).map((o) => (
                  <OrderCard
                    key={o.id}
                    order={o}
                    actions={orderActions.actionsFor(o)}
                    busy={orderActions.busyId === o.id}
                    onDetail={() => orderActions.openDetail(o.id)}
                    showMoney
                    hint={orderHint(o, 'WORKSHOP_ACCOUNTANT')}
                    onMenuAction={orderActions.runFromMenu}
                  />
                ))}
              </CardGrid>
            )}
          </Section>

          <Section title="Tài chính" extra={<Link to="/finance">Chi tiết</Link>}>
            <StatStrip items={finance} aria-label="Tóm tắt tài chính" />
          </Section>

          <Section title="Đơn theo trạng thái">
            <StatusStrip counts={data.countsByStatus} linkFor={(s) => `/orders?status=${s}`} />
          </Section>
        </>
      )}

      {orderActions.ui}
    </>
  )
}
