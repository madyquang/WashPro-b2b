import type { ReactNode } from 'react'
import ToneTag from './ToneTag'
import type { StatusTone } from '../theme'
import { CATEGORY_META, type ItemCategory } from '../types'

/** Màu nhóm đồ giặt (đạt tương phản; màu preset cũ của antd như cam / xanh ngọc chỉ ~3.3:1). */
const CATEGORY_TONE: Record<ItemCategory, StatusTone> = {
  TABLE_LINEN: 'orange',
  BEDDING: 'blue',
  TOWEL: 'teal',
  UNIFORM: 'purple',
  OTHER: 'gray',
}

/** Nhãn nhóm đồ; children thay cho tên nhóm khi cần (ví dụ "Chăn ga gối · 3"). */
export default function CategoryTag({ category, children }: { category: ItemCategory; children?: ReactNode }) {
  return <ToneTag tone={CATEGORY_TONE[category]}>{children ?? CATEGORY_META[category].label}</ToneTag>
}
