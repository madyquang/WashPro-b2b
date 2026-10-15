import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Tabs, Typography } from 'antd'
import { message } from '../utils/message'
import type { TableColumnsType } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import axiosClient from '../api/axiosClient'
import LoadError from '../components/LoadError'
import CategoryTag from '../components/CategoryTag'
import PageHeader from '../components/PageHeader'
import ResponsiveTable from '../components/ResponsiveTable'
import ToneTag from '../components/ToneTag'
import { formatCurrency, getErrorMessage, moneyFormatter, moneyParser } from '../utils/format'
import {
  CATEGORY_META,
  CATEGORY_ORDER,
  CUSTOMER_TYPE_LABELS,
  SERVICE_TYPE_LABELS,
  UNIT_META,
  type CustomerPriceRow,
  type ItemCategory,
  type PriceUnit,
  type Restaurant,
  type ServicePrice,
  type ServiceType,
} from '../types'

interface PriceForm {
  name: string
  category: ItemCategory
  serviceType: ServiceType
  unit: PriceUnit
  unitPrice: number
}

const CATEGORY_OPTIONS = CATEGORY_ORDER.map((c) => ({ value: c, label: CATEGORY_META[c].label }))
const SERVICE_OPTIONS = (Object.keys(SERVICE_TYPE_LABELS) as ServiceType[]).map((s) => ({ value: s, label: SERVICE_TYPE_LABELS[s] }))
const UNIT_OPTIONS = (Object.keys(UNIT_META) as PriceUnit[]).map((u) => ({ value: u, label: UNIT_META[u].label }))

const categoryColumn = {
  title: 'Nhóm đồ',
  dataIndex: 'category',
  width: 190,
  render: (c: ItemCategory) => <CategoryTag category={c} />,
}

/** Lọc theo nhóm đồ (đặt ở thanh lọc thay vì tiêu đề cột để dùng được cả trên điện thoại). */
function CategoryFilter({ value, onChange }: { value?: ItemCategory; onChange: (v?: ItemCategory) => void }) {
  return <Select allowClear aria-label="Lọc theo nhóm đồ" placeholder="Lọc theo nhóm đồ" style={{ width: 200 }} options={CATEGORY_OPTIONS} value={value} onChange={onChange} />
}

export default function Prices() {
  return (
    <>
      <PageHeader
        title="Bảng giá dịch vụ"
        description="Giá chung cho từng loại đồ và dịch vụ; giá riêng theo hợp đồng của từng khách hàng."
      />
      <Tabs
        items={[
          { key: 'base', label: 'Bảng giá chung', children: <BasePrices /> },
          { key: 'customer', label: 'Giá riêng theo khách hàng', children: <CustomerPrices /> },
        ]}
      />
    </>
  )
}

const activeTag = (active: boolean) => (active ? <ToneTag tone="green">Đang áp dụng</ToneTag> : <ToneTag tone="gray">Đã ẩn</ToneTag>)

/** Bảng giá chung: mỗi dòng là một loại đồ + dịch vụ + đơn vị tính. */
function BasePrices() {
  const [rows, setRows] = useState<ServicePrice[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState<ItemCategory | undefined>()
  const [editing, setEditing] = useState<ServicePrice | 'new' | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<PriceForm>()

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.get<ServicePrice[]>('/prices')
      setRows(data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được bảng giá'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const visible = useMemo(() => {
    const k = search.trim().toLowerCase()
    return rows.filter((r) => (!category || r.category === category) && (!k || r.name.toLowerCase().includes(k)))
  }, [rows, search, category])

  const openEditor = (target: ServicePrice | 'new') => {
    setEditing(target)
    form.setFieldsValue(
      target === 'new'
        ? { name: '', category: 'BEDDING', serviceType: 'WASH', unit: 'PIECE', unitPrice: undefined }
        : { name: target.name, category: target.category, serviceType: target.serviceType, unit: target.unit, unitPrice: target.unitPrice },
    )
  }

  const save = async (values: PriceForm) => {
    setSaving(true)
    try {
      const body = { ...values, name: values.name.trim() }
      if (editing === 'new') {
        await axiosClient.post('/prices', body)
        message.success('Đã thêm vào bảng giá')
      } else if (editing) {
        await axiosClient.put(`/prices/${editing.id}`, body)
        message.success('Đã cập nhật (đơn cũ giữ nguyên giá đã chốt)')
      }
      setEditing(null)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const toggle = async (p: ServicePrice) => {
    try {
      await axiosClient.patch(`/prices/${p.id}/active`, { enabled: !p.active })
      message.success(p.active ? 'Đã ẩn mục này' : 'Đã hiển thị lại mục này')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: TableColumnsType<ServicePrice> = [
    categoryColumn,
    { title: 'Loại đồ', dataIndex: 'name', sorter: (a, b) => a.name.localeCompare(b.name, 'vi') },
    { title: 'Dịch vụ', dataIndex: 'serviceType', render: (s: ServiceType) => SERVICE_TYPE_LABELS[s] },
    { title: 'Đơn giá', dataIndex: 'unitPrice', align: 'right', render: (v: number, p) => `${formatCurrency(v)}/${UNIT_META[p.unit].label}`, sorter: (a, b) => a.unitPrice - b.unitPrice },
    { title: 'Trạng thái', dataIndex: 'active', width: 130, render: (a: boolean) => activeTag(a) },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_: unknown, p) => (
        <Space size={4}>
          <Button size="small" onClick={() => openEditor(p)}>
            Sửa
          </Button>
          <Popconfirm title={p.active ? 'Ẩn mục này?' : 'Hiển thị lại mục này?'} description={p.active ? 'Đơn cũ không bị ảnh hưởng, nhưng không chọn được cho đơn mới.' : undefined} okText="Đồng ý" cancelText="Đóng" onConfirm={() => toggle(p)}>
            <Button size="small" type="text" danger={p.active}>
              {p.active ? 'Ẩn' : 'Hiện'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <div className="filter-bar">
        <Input.Search allowClear aria-label="Tìm loại đồ" placeholder="Tìm loại đồ" style={{ width: 240 }} onChange={(e) => setSearch(e.target.value)} />
        <CategoryFilter value={category} onChange={setCategory} />
        <span style={{ flex: '1 0 0' }} aria-hidden />
        <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
          Làm mới
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('new')}>
          Thêm vào bảng giá
        </Button>
      </div>

      <LoadError error={error} onRetry={load} />

      <ResponsiveTable<ServicePrice>
        table={{
          rowKey: 'id',
          loading,
          columns,
          dataSource: visible,
          scroll: { x: 'max-content' },
          pagination: { pageSize: 20, showSizeChanger: false },
        }}
        cardTitle={(p) => p.name}
        cardExtra={(p) => activeTag(p.active)}
        hideInCard={['name', 'active']}
        emptyText={search || category ? 'Không có mục nào khớp bộ lọc.' : 'Chưa có mục nào trong bảng giá.'}
        emptyAction={search || category ? undefined : { label: 'Thêm vào bảng giá', icon: <PlusOutlined />, onClick: () => openEditor('new') }}
      />

      <Modal
        title={editing === 'new' ? 'Thêm vào bảng giá' : 'Sửa mục bảng giá'}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={() => form.submit()}
        okText="Lưu"
        cancelText="Hủy"
        confirmLoading={saving}
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
        forceRender
      >
        <Form<PriceForm> form={form} layout="vertical" onFinish={save}>
          <Form.Item name="category" label="Nhóm đồ" rules={[{ required: true, message: 'Vui lòng chọn nhóm đồ' }]}>
            <Select options={CATEGORY_OPTIONS} />
          </Form.Item>
          <Form.Item name="name" label="Tên loại đồ" rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập tên' }]}>
            <Input maxLength={100} placeholder="Ví dụ: Ga giường đôi, Áo đầu bếp, Chăn ga (tính cân)" />
          </Form.Item>
          <Form.Item name="serviceType" label="Dịch vụ" rules={[{ required: true, message: 'Vui lòng chọn dịch vụ' }]} extra="Cùng một loại đồ có thể thêm nhiều dòng với dịch vụ khác nhau.">
            <Select options={SERVICE_OPTIONS} />
          </Form.Item>
          <Form.Item name="unit" label="Đơn vị tính" rules={[{ required: true, message: 'Vui lòng chọn đơn vị' }]} extra="Chọn kg nếu tính tiền theo cân (chăn ga, đồng phục giặt số lượng lớn).">
            <Select options={UNIT_OPTIONS} />
          </Form.Item>
          <Form.Item name="unitPrice" label="Đơn giá chung (đ)" rules={[{ required: true, message: 'Vui lòng nhập đơn giá' }]}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} formatter={moneyFormatter} parser={moneyParser} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

/** Giá hợp đồng riêng: ghi đè giá chung cho một khách hàng. */
function CustomerPrices() {
  const [customers, setCustomers] = useState<Restaurant[]>([])
  const [customerId, setCustomerId] = useState<number | undefined>()
  const [rows, setRows] = useState<CustomerPriceRow[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<CustomerPriceRow | null>(null)
  const [price, setPrice] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [onlyCustom, setOnlyCustom] = useState(false)
  const [category, setCategory] = useState<ItemCategory | undefined>()

  useEffect(() => {
    axiosClient
      .get<Restaurant[]>('/restaurants')
      .then((res) => setCustomers(res.data))
      .catch((e) => message.error(getErrorMessage(e, 'Không tải được danh sách khách hàng')))
  }, [])

  const load = useCallback(async (id: number) => {
    setLoading(true)
    setError(null)
    try {
      const { data } = await axiosClient.get<CustomerPriceRow[]>(`/prices/customers/${id}`)
      setRows(data)
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được bảng giá của khách hàng'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (customerId !== undefined) load(customerId)
  }, [customerId, load])

  const replaceRow = (row: CustomerPriceRow) =>
    setRows((prev) => prev.map((r) => (r.servicePriceId === row.servicePriceId ? row : r)))

  const save = async () => {
    if (!editing || customerId === undefined || price === null) return
    setSaving(true)
    try {
      const { data } = await axiosClient.put<CustomerPriceRow>(`/prices/customers/${customerId}/${editing.servicePriceId}`, { unitPrice: price })
      replaceRow(data)
      message.success('Đã đặt giá riêng (áp dụng cho các lần nhập giá tiếp theo)')
      setEditing(null)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const reset = async (row: CustomerPriceRow) => {
    if (customerId === undefined) return
    try {
      const { data } = await axiosClient.delete<CustomerPriceRow>(`/prices/customers/${customerId}/${row.servicePriceId}`)
      replaceRow(data)
      message.success('Đã quay về giá chung')
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const visible = rows.filter((r) => (!onlyCustom || r.customPrice !== null) && (!category || r.category === category))

  const columns: TableColumnsType<CustomerPriceRow> = [
    categoryColumn,
    {
      title: 'Loại đồ',
      key: 'name',
      render: (_: unknown, r) => (
        <>
          {r.name} · {SERVICE_TYPE_LABELS[r.serviceType]} {!r.active && <ToneTag tone="gray">Đã ẩn</ToneTag>}
        </>
      ),
    },
    { title: 'Giá chung', dataIndex: 'basePrice', align: 'right', render: (v: number, r) => `${formatCurrency(v)}/${UNIT_META[r.unit].label}` },
    {
      title: 'Giá riêng',
      dataIndex: 'customPrice',
      align: 'right',
      render: (v: number | null, r) =>
        v === null ? <Typography.Text type="secondary">—</Typography.Text> : <Typography.Text strong type={v < r.basePrice ? 'success' : undefined}>{formatCurrency(v)}/{UNIT_META[r.unit].label}</Typography.Text>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_: unknown, r) => (
        <Space size={4}>
          <Button
            size="small"
            onClick={() => {
              setEditing(r)
              setPrice(r.customPrice ?? r.basePrice)
            }}
          >
            {r.customPrice === null ? 'Đặt giá riêng' : 'Sửa'}
          </Button>
          {r.customPrice !== null && (
            <Popconfirm title="Bỏ giá riêng, dùng lại giá chung?" okText="Đồng ý" cancelText="Đóng" onConfirm={() => reset(r)}>
              <Button size="small" type="text" danger>
                Bỏ
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <div className="filter-bar">
        <Select
          showSearch={{ optionFilterProp: 'label' }}
          aria-label="Chọn khách hàng"
          placeholder="Chọn khách hàng"
          style={{ width: 320, maxWidth: '100%' }}
          value={customerId}
          onChange={setCustomerId}
          options={customers.map((c) => ({ value: c.id, label: `${c.name} (${CUSTOMER_TYPE_LABELS[c.customerType]})` }))}
        />
        <CategoryFilter value={category} onChange={setCategory} />
        <Select
          aria-label="Phạm vi bảng giá"
          style={{ width: 200 }}
          value={onlyCustom}
          onChange={setOnlyCustom}
          options={[
            { value: false, label: 'Toàn bộ bảng giá' },
            { value: true, label: 'Chỉ mục có giá riêng' },
          ]}
        />
        {customerId !== undefined && (
          <Button icon={<ReloadOutlined />} onClick={() => load(customerId)} loading={loading}>
            Làm mới
          </Button>
        )}
      </div>

      <Typography.Paragraph type="secondary">
        Khách hàng có hợp đồng giá khác (ví dụ khách sạn gửi chăn ga số lượng lớn) thì đặt giá riêng tại đây. Khi tài xế tạo đơn hoặc kế toán chọn loại đồ,
        hệ thống tự áp giá riêng; mục không có giá riêng dùng giá chung. Đổi giá không làm thay đổi đơn đã nhập giá.
      </Typography.Paragraph>

      {error && <Alert type="error" showIcon className="mb-4" title={error} />}

      {customerId === undefined ? (
        <Alert type="info" showIcon title="Chọn một khách hàng để xem và đặt giá riêng." />
      ) : (
        <ResponsiveTable<CustomerPriceRow>
          table={{
            rowKey: 'servicePriceId',
            loading,
            columns,
            dataSource: visible,
            scroll: { x: 'max-content' },
            pagination: { pageSize: 20, showSizeChanger: false },
          }}
          cardTitle={(r) => `${r.name} · ${SERVICE_TYPE_LABELS[r.serviceType]}`}
          cardExtra={(r) => (!r.active ? <ToneTag tone="gray">Đã ẩn</ToneTag> : null)}
          hideInCard={['name']}
          emptyText={onlyCustom ? 'Khách hàng này chưa có giá riêng nào.' : category ? 'Không có mục nào thuộc nhóm này.' : 'Bảng giá chung đang trống.'}
        />
      )}

      <Modal
        title={editing ? `Giá riêng · ${editing.name} · ${SERVICE_TYPE_LABELS[editing.serviceType]}` : ''}
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={save}
        okText="Lưu"
        cancelText="Hủy"
        okButtonProps={{ disabled: price === null }}
        confirmLoading={saving}
        className="sheet-modal"
        width={{ xs: '100%', md: 520 }}
        destroyOnHidden
      >
        {editing && (
          <>
            <Typography.Paragraph>
              Giá chung: <b>{formatCurrency(editing.basePrice)}/{UNIT_META[editing.unit].label}</b>
            </Typography.Paragraph>
            <InputNumber
              aria-label="Giá riêng cho khách hàng (đ)"
              autoFocus
              min={0}
              precision={0}
              style={{ width: '100%' }}
              formatter={moneyFormatter}
              parser={moneyParser}
              suffix={`đ/${UNIT_META[editing.unit].label}`}
              value={price}
              onChange={(v) => setPrice(v)}
            />
          </>
        )}
      </Modal>
    </>
  )
}
