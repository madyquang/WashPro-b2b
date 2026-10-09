import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button, Col, Row, Skeleton, Tabs } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import EmptyState from '../components/EmptyState'
import PageHeader from '../components/PageHeader'
import { isActivePickup, usePickupActions } from './pickups/usePickupActions'
import { getSession, isRestaurantRole } from '../utils/auth'
import { getErrorMessage } from '../utils/format'
import type { PickupRequest, PickupStatus } from '../types'

interface PickupTab {
  key: string
  label: string
  match: (r: PickupRequest) => boolean
  empty: string
}

const is = (...statuses: PickupStatus[]) => (r: PickupRequest) => statuses.includes(r.status)

/** Yêu cầu lấy đồ: khách tạo, tài xế nhận việc rồi tạo đơn, quản lý xưởng điều phối. */
export default function Pickups() {
  const session = getSession()
  const role = session?.role
  const isCustomer = role ? isRestaurantRole(role) : false
  const isDriver = role === 'DRIVER'
  const isAdmin = role === 'WORKSHOP_ADMIN'
  const [params, setParams] = useSearchParams()

  const [rows, setRows] = useState<PickupRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.get<PickupRequest[]>('/pickups')
      setRows(data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được yêu cầu lấy đồ'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const pickups = usePickupActions(load)
  const { openCreate, canCreate } = pickups

  // Tab theo việc của từng vai trò: tài xế "việc của tôi" trước, xưởng "chưa có tài xế" trước
  const tabs: PickupTab[] = isDriver
    ? [
        { key: 'mine', label: 'Việc của tôi', match: is('ACCEPTED'), empty: 'Bạn chưa nhận việc nào.' },
        { key: 'open', label: 'Chờ nhận', match: is('OPEN'), empty: 'Hiện không có yêu cầu nào đang chờ tài xế.' },
        { key: 'done', label: 'Đã xong', match: is('DONE', 'CANCELLED'), empty: 'Chưa có việc nào đã xong.' },
      ]
    : isAdmin
      ? [
          { key: 'open', label: 'Chưa có tài xế', match: is('OPEN'), empty: 'Mọi yêu cầu đều đã có tài xế.' },
          { key: 'assigned', label: 'Đã có tài xế', match: is('ACCEPTED'), empty: 'Không có yêu cầu nào đang chờ tài xế đến lấy.' },
          { key: 'done', label: 'Đã xong / đã hủy', match: is('DONE', 'CANCELLED'), empty: 'Chưa có yêu cầu nào đã xong hoặc đã hủy.' },
        ]
      : [
          { key: 'active', label: 'Đang chờ lấy', match: isActivePickup, empty: 'Bạn chưa có yêu cầu lấy đồ nào đang chờ.' },
          { key: 'done', label: 'Đã xong / đã hủy', match: is('DONE', 'CANCELLED'), empty: 'Chưa có yêu cầu nào đã xong hoặc đã hủy.' },
        ]
  // Không chỉ định tab: mở tab đầu tiên đang có việc (tránh mở vào một tab trống khi tab bên cạnh có việc)
  const tabKey =
    tabs.find((t) => t.key === params.get('tab'))?.key ?? tabs.find((t) => t.key !== 'done' && rows.some(t.match))?.key ?? tabs[0].key
  const tab = tabs.find((t) => t.key === tabKey) ?? tabs[0]
  const setTab = (key: string) => {
    const next = new URLSearchParams(params)
    next.set('tab', key)
    setParams(next, { replace: true })
  }

  // Việc đang chờ: ngày hẹn gần nhất lên đầu; việc đã xong: mới nhất lên đầu (danh sách nhỏ, lọc mỗi lần render là đủ)
  const matched = rows.filter(tab.match)
  const visible = tab.key === 'done' ? matched : matched.sort((a, b) => a.preferredDate.localeCompare(b.preferredDate) || a.id - b.id)

  // Link /pickups?new=1 (nút "Yêu cầu lấy đồ" ở nơi khác) mở thẳng form, không phải bấm thêm lần nữa
  const wantsNew = params.get('new') === '1'
  useEffect(() => {
    if (!wantsNew) return
    if (canCreate) openCreate()
    const next = new URLSearchParams(params)
    next.delete('new')
    setParams(next, { replace: true })
  }, [wantsNew, canCreate, openCreate, params, setParams])

  if (!role) return null

  const emptyAction =
    tab.key === 'mine'
      ? { label: 'Xem việc chờ nhận', onClick: () => setTab('open') }
      : canCreate && tab.key !== 'done'
        ? { label: isAdmin ? 'Tạo yêu cầu hộ khách' : 'Yêu cầu lấy đồ', icon: <PlusOutlined />, onClick: openCreate }
        : undefined

  return (
    <>
      <PageHeader
        title={isDriver ? 'Việc lấy đồ' : 'Lấy đồ'}
        description={
          (isCustomer && 'Gửi yêu cầu khi cần xưởng đến lấy đồ bẩn, theo dõi khi nào tài xế đến.') ||
          (isDriver && 'Nhận việc, gọi / chỉ đường đến khách, lấy đồ rồi tạo đơn ngay tại chỗ.') ||
          (isAdmin && 'Phân công tài xế cho yêu cầu lấy đồ, ưu tiên yêu cầu chưa có tài xế và quá ngày hẹn.') ||
          undefined
        }
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              Làm mới
            </Button>
            {canCreate && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                {isAdmin ? 'Tạo yêu cầu hộ khách' : 'Yêu cầu lấy đồ'}
              </Button>
            )}
          </>
        }
      />

      <LoadError error={error} onRetry={load} />

      <Tabs
        activeKey={tab.key}
        onChange={setTab}
        items={tabs.map((t) => ({ key: t.key, label: t.key === 'done' ? t.label : `${t.label} (${rows.filter(t.match).length})` }))}
      />

      {loading && rows.length === 0 ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : visible.length === 0 ? (
        <EmptyState description={tab.empty} action={emptyAction} />
      ) : (
        <Row gutter={[12, 12]}>
          {visible.map((r) => (
            <Col key={r.id} xs={24} sm={12} xl={8}>
              {pickups.cardFor(r)}
            </Col>
          ))}
        </Row>
      )}

      {pickups.ui}
    </>
  )
}
