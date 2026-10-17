import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button, Card, Checkbox, Segmented, Skeleton, Space, Typography } from 'antd'
import { ArrowDownOutlined, ArrowUpOutlined, EnvironmentOutlined, PhoneOutlined, RollbackOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import EmptyState from '../components/EmptyState'
import LoadError from '../components/LoadError'
import PageHeader from '../components/PageHeader'
import ToneTag from '../components/ToneTag'
import { getErrorMessage, todayIso } from '../utils/format'
import { applyOrder, loadOrder, mapsLinks, saveOrder, stopKey } from '../utils/route'
import { TIME_SLOT_LABELS, type RoutePlan as Plan, type RouteStop } from '../types'

type Day = 'today' | 'tomorrow'

const tomorrowIso = () => {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const singleMap = (s: RouteStop) => `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(s.address || s.customerName)}`

/** Lộ trình của tài xế: gom điểm theo khu vực, tự sắp lại thứ tự, mở Google Maps nhiều điểm dừng. */
export default function RoutePlan() {
  const navigate = useNavigate()
  const [day, setDay] = useState<Day>('today')
  const date = day === 'today' ? todayIso() : tomorrowIso()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [custom, setCustom] = useState<string[] | null>(null)
  const [backToWorkshop, setBackToWorkshop] = useState(true)

  useEffect(() => {
    let ignore = false
    setError(null)
    setPlan(null)
    axiosClient
      .get<Plan>('/routes', { params: { date } })
      .then(({ data }) => {
        if (ignore) return
        setPlan(data)
        setCustom(loadOrder(data.driverId, date))
      })
      .catch((e) => !ignore && setError(getErrorMessage(e, 'Không tải được lộ trình')))
    return () => {
      ignore = true
    }
  }, [date, reloadKey])

  const stops = useMemo(() => (plan ? applyOrder(plan.stops, custom) : []), [plan, custom])
  const areas = new Set(stops.map((s) => s.area ?? '?')).size
  const links = mapsLinks(stops, backToWorkshop ? plan?.workshopAddress : null)

  const move = (index: number, delta: number) => {
    if (!plan) return
    const keys = stops.map(stopKey)
    const [k] = keys.splice(index, 1)
    keys.splice(index + delta, 0, k)
    setCustom(keys)
    saveOrder(plan.driverId, date, keys)
  }

  const reset = () => {
    if (!plan) return
    setCustom(null)
    saveOrder(plan.driverId, date, null)
  }

  return (
    <div className="narrow">
      <PageHeader title="Lộ trình" description="Các điểm lấy đồ đã nhận việc và đơn đang giao, gom theo khu vực. Bấm mũi tên để đổi thứ tự đi." />

      <Segmented<Day>
        block
        className="mb-4"
        value={day}
        onChange={setDay}
        options={[
          { value: 'today', label: 'Hôm nay' },
          { value: 'tomorrow', label: 'Ngày mai' },
        ]}
      />

      <LoadError error={error} onRetry={() => setReloadKey((k) => k + 1)} />
      {!plan && !error && <Skeleton active paragraph={{ rows: 6 }} />}
      {plan && stops.length === 0 && (
        <EmptyState
          description={day === 'today' ? 'Chưa có điểm nào. Nhận việc ở mục Lấy & giao để thêm điểm vào lộ trình.' : 'Chưa có việc hẹn ngày mai.'}
          action={day === 'today' ? { label: 'Xem việc chờ nhận', onClick: () => navigate('/pickups?tab=open') } : undefined}
        />
      )}

      {plan && stops.length > 0 && (
        <>
          <Card size="small" className="mb-4">
            <Space orientation="vertical" style={{ width: '100%' }}>
              <Typography.Text>
                <strong>{stops.length}</strong> điểm · {areas} khu vực{custom ? ' · đã sắp lại thứ tự' : ''}
              </Typography.Text>
              {plan.workshopAddress && (
                <Checkbox checked={backToWorkshop} onChange={(e) => setBackToWorkshop(e.target.checked)}>
                  Kết thúc ở xưởng ({plan.workshopAddress})
                </Checkbox>
              )}
              <Space wrap>
                {links.map((l) => (
                  <a key={l.url} href={l.url} target="_blank" rel="noreferrer">
                    <Button type="primary" icon={<EnvironmentOutlined />}>
                      Mở Google Maps · {l.label}
                    </Button>
                  </a>
                ))}
                {custom && (
                  <Button icon={<RollbackOutlined />} onClick={reset}>
                    Về thứ tự gợi ý
                  </Button>
                )}
              </Space>
            </Space>
          </Card>

          <ol className="route-list">
            {stops.map((s, i) => (
              <li key={stopKey(s)} className="route-stop" data-key={stopKey(s)}>
                <span className="route-index" aria-hidden>
                  {i + 1}
                </span>
                <div className="route-body">
                  <div className="route-title">
                    <strong>{s.customerName}</strong>
                    <Space size={4} wrap>
                      <ToneTag tone={s.type === 'PICKUP' ? 'blue' : 'teal'}>{s.type === 'PICKUP' ? 'Lấy đồ' : 'Giao trả'}</ToneTag>
                      {s.late && <ToneTag tone="red">Quá ngày hẹn</ToneTag>}
                      <ToneTag tone="gray">{s.area ?? 'Chưa rõ khu vực'}</ToneTag>
                    </Space>
                  </div>
                  <div className="route-meta">
                    {s.address ?? 'Chưa có địa chỉ'}
                    {s.timeSlot && ` · ${TIME_SLOT_LABELS[s.timeSlot]}`}
                    {s.bags != null && ` · ${s.bags} túi`}
                  </div>
                  {s.note && <div className="route-meta">Khách dặn: {s.note}</div>}
                  <Space wrap size={4} className="route-actions">
                    {s.phone && (
                      <a href={`tel:${s.phone}`}>
                        <Button size="small" icon={<PhoneOutlined />}>
                          Gọi
                        </Button>
                      </a>
                    )}
                    <a href={singleMap(s)} target="_blank" rel="noreferrer">
                      <Button size="small" icon={<EnvironmentOutlined />}>
                        Chỉ đường
                      </Button>
                    </a>
                    <Link to="/pickups?tab=mine">
                      <Button size="small">Mở việc</Button>
                    </Link>
                  </Space>
                </div>
                <div className="route-order">
                  <Button icon={<ArrowUpOutlined />} aria-label={`Đưa ${s.customerName} lên trước`} disabled={i === 0} onClick={() => move(i, -1)} />
                  <Button icon={<ArrowDownOutlined />} aria-label={`Đưa ${s.customerName} xuống sau`} disabled={i === stops.length - 1} onClick={() => move(i, 1)} />
                </div>
              </li>
            ))}
          </ol>
        </>
      )}
    </div>
  )
}
