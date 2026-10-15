import { useEffect, useState } from 'react'
import { message } from 'antd'
import axiosClient from '../api/axiosClient'
import { getErrorMessage } from './format'
import {
  CATEGORY_META,
  CATEGORY_ORDER,
  SERVICE_TYPE_LABELS,
  UNIT_META,
  type CatalogItem,
  type ItemCategory,
  type PriceUnit,
  type ServiceType,
} from '../types'

/** "Ga giường đôi · Giặt + là ủi" (nhận cả mục bảng giá lẫn dòng đồ trong đơn). */
export const catalogLabel = (item: { serviceType: ServiceType; name?: string; itemName?: string }) =>
  `${item.name ?? item.itemName ?? ''} · ${SERVICE_TYPE_LABELS[item.serviceType]}`

/** Giá trị chọn "đồ ngoài bảng giá" trong ô chọn loại đồ. */
export const CUSTOM_ITEM = -1

/** Một dòng đồ giặt trong form. */
export interface LineValue {
  servicePriceId?: number
  itemName?: string
  unit?: PriceUnit
  quantity?: number
  unitPrice?: number | null
}

/** Đơn vị của một dòng: theo bảng giá, hoặc theo lựa chọn khi nhập tay. */
export const lineUnit = (line: LineValue | undefined, catalog: CatalogItem[]): PriceUnit => {
  if (line?.servicePriceId && line.servicePriceId !== CUSTOM_ITEM) {
    return catalog.find((c) => c.id === line.servicePriceId)?.unit ?? 'PIECE'
  }
  return line?.unit ?? 'PIECE'
}

/** Chuyển dòng trong form thành dữ liệu gửi API. */
export const toItemRequest = (line: LineValue, withPrice = false) => {
  const custom = !line.servicePriceId || line.servicePriceId === CUSTOM_ITEM
  return {
    servicePriceId: custom ? null : line.servicePriceId,
    itemName: custom ? (line.itemName ?? '').trim() : null,
    unit: custom ? (line.unit ?? 'PIECE') : null,
    quantity: line.quantity,
    unitPrice: withPrice ? (line.unitPrice ?? null) : null,
  }
}

const quantityFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 })

/** "12,5 kg", "20 cái" */
export const formatQuantity = (quantity: number, unit: PriceUnit) =>
  `${quantityFormat.format(quantity)} ${UNIT_META[unit].label}`

/** Gom theo nhóm đồ, đúng thứ tự nhóm; bỏ nhóm rỗng. */
export function groupByCategory<T extends { category: ItemCategory }>(items: T[]) {
  return CATEGORY_ORDER.map((category) => ({
    category,
    label: CATEGORY_META[category].label,
    items: items.filter((i) => i.category === category),
  })).filter((g) => g.items.length > 0)
}

/** Tùy chọn cho Select, chia nhóm theo loại đồ. */
export const catalogOptions = (catalog: CatalogItem[]) =>
  groupByCategory(catalog).map((g) => ({
    label: g.label,
    title: g.label,
    options: g.items.map((i) => ({ value: i.id, label: `${catalogLabel(i)} (${UNIT_META[i.unit].label})` })),
  }))

/** Danh mục đồ giặt đang áp dụng (không có giá), mọi vai trò đều xem được. */
export function useCatalog(enabled = true) {
  const [catalog, setCatalog] = useState<CatalogItem[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!enabled) return
    let ignore = false
    setLoading(true)
    axiosClient
      .get<CatalogItem[]>('/prices/catalog')
      .then((res) => !ignore && setCatalog(res.data))
      .catch((e) => !ignore && message.error(getErrorMessage(e, 'Không tải được danh mục đồ giặt')))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [enabled])

  return { catalog, loading }
}
