import { useEffect, useState } from 'react'
import { Input, Segmented, Space, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import PageHeader from '../components/PageHeader'
import ResponsiveTable from '../components/ResponsiveTable'
import ToneTag from '../components/ToneTag'
import type { StatusTone } from '../theme'
import { formatDateTime, getErrorMessage, todayIso } from '../utils/format'
import { ROLE_LABELS, type AuditCategory, type AuditEntry, type Role } from '../types'

const PAGE_SIZE = 30

const CATEGORIES: { value: AuditCategory | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PRICE', label: 'Bảng giá' },
  { value: 'PAYMENT', label: 'Thu tiền' },
  { value: 'ORDER', label: 'Đơn hàng' },
  { value: 'CUSTOMER', label: 'Khách hàng' },
  { value: 'ACCOUNT', label: 'Tài khoản' },
]

const TONES: Record<AuditCategory, StatusTone> = { PRICE: 'gold', PAYMENT: 'green', ORDER: 'blue', CUSTOMER: 'teal', ACCOUNT: 'purple' }

/** Nhật ký thay đổi về giá, tiền, hạn, hủy đơn, khách hàng, tài khoản (quản lý xưởng, kế toán). */
export default function AuditLog() {
  const [category, setCategory] = useState<AuditCategory | 'ALL'>('ALL')
  const [search, setSearch] = useState('')
  const [keyword, setKeyword] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [paging, setPaging] = useState({ key: '', page: 1 })
  const [data, setData] = useState<{ content: AuditEntry[]; total: number } | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  // Gõ tìm kiếm: chờ người dùng ngừng gõ một chút rồi mới gọi API
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(search.trim()), 350)
    return () => clearTimeout(timer)
  }, [search])

  // Đổi bộ lọc thì về trang đầu
  const filterKey = [category, keyword, from, to].join('|')
  const page = paging.key === filterKey ? paging.page : 1

  useEffect(() => {
    let ignore = false
    setLoading(true)
    setError(null)
    axiosClient
      .get<{ content: AuditEntry[]; total: number }>('/audit', {
        params: {
          category: category === 'ALL' ? undefined : category,
          q: keyword || undefined,
          from: from || undefined,
          to: to || undefined,
          page: page - 1,
          size: PAGE_SIZE,
        },
      })
      .then(({ data }) => !ignore && setData(data))
      .catch((e) => !ignore && setError(getErrorMessage(e, 'Không tải được nhật ký')))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [category, keyword, from, to, page, reloadKey])

  const columns: TableColumnsType<AuditEntry> = [
    { title: 'Thời gian', dataIndex: 'createdAt', key: 'createdAt', width: 150, render: (v: string) => formatDateTime(v) },
    {
      title: 'Người thực hiện',
      key: 'actor',
      width: 170,
      render: (_: unknown, e) => (
        <>
          <div>{e.actor}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {ROLE_LABELS[e.actorRole as Role] ?? e.actorRole}
          </Typography.Text>
        </>
      ),
    },
    { title: 'Hành động', key: 'action', width: 190, render: (_: unknown, e) => <ToneTag tone={TONES[e.category]}>{e.actionLabel}</ToneTag> },
    {
      title: 'Nội dung',
      key: 'summary',
      render: (_: unknown, e) => (
        <>
          <div>{e.summary}</div>
          {e.details && (
            <Typography.Text type="secondary" className="pre-wrap" style={{ fontSize: 13 }}>
              {e.details}
            </Typography.Text>
          )}
        </>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Nhật ký thay đổi"
        description="Ai đã đổi giá, thu / hủy tiền, đổi hạn, hủy đơn, sửa khách hàng hoặc tài khoản, lúc nào, từ giá trị nào sang giá trị nào. Chỉ xem, không sửa được."
      />

      <Space orientation="vertical" style={{ width: '100%' }} className="mb-4">
        <Segmented<AuditCategory | 'ALL'> value={category} onChange={setCategory} options={CATEGORIES} />
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="Tìm người, tên khách, mã đơn, nội dung..."
            aria-label="Tìm trong nhật ký"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 320, maxWidth: '100%' }}
          />
          <Input type="date" aria-label="Từ ngày" value={from} max={to || todayIso()} onChange={(e) => setFrom(e.target.value)} />
          <Input type="date" aria-label="Đến ngày" value={to} min={from} max={todayIso()} onChange={(e) => setTo(e.target.value)} />
        </Space>
      </Space>

      <LoadError error={error} onRetry={() => setReloadKey((k) => k + 1)} />
      <ResponsiveTable<AuditEntry>
        table={{
          rowKey: 'id',
          columns,
          dataSource: data?.content ?? [],
          loading,
          size: 'small',
          scroll: { x: 'max-content' },
          pagination: {
            current: page,
            pageSize: PAGE_SIZE,
            total: data?.total ?? 0,
            showSizeChanger: false,
            onChange: (p) => setPaging({ key: filterKey, page: p }),
            showTotal: (t) => `${t} thay đổi`,
          },
        }}
        cardTitle={(e) => e.summary}
        cardExtra={(e) => <ToneTag tone={TONES[e.category]}>{e.actionLabel}</ToneTag>}
        hideInCard={['action']}
        emptyText="Chưa có thay đổi nào khớp bộ lọc."
      />
    </div>
  )
}
