import { useState, type Key, type ReactNode } from 'react'
import { Card, Grid, Pagination, Skeleton, Table } from 'antd'
import type { TableColumnType, TableProps } from 'antd'
import EmptyState from './EmptyState'

/** Bên trong component dùng kiểu dòng chung (không generic) để TypeScript không phải suy luận kiểu bảng antd quá sâu. */
type Row = Record<string, unknown>

interface Props<T> {
  /** Props của bảng antd (giữ nguyên như khi dùng Table trực tiếp). */
  table: TableProps<T>
  /** Tiêu đề thẻ trên điện thoại (thường là tên / mã). */
  cardTitle: (row: T) => ReactNode
  /** Phần góc phải tiêu đề thẻ (thường là nhãn trạng thái). */
  cardExtra?: (row: T) => ReactNode
  /** key của cột thao tác: hiện ở cuối thẻ thay vì một dòng "nhãn: giá trị". */
  actionsKey?: string
  /** key / dataIndex của các cột không lặp lại trong thẻ (đã nằm ở tiêu đề / góc phải). */
  hideInCard?: string[]
  emptyText: string
  emptyAction?: { label: string; icon?: ReactNode; onClick: () => void }
}

/**
 * Bảng trên máy tính / máy tính bảng; trên điện thoại (< 768px) mỗi dòng thành một thẻ "nhãn: giá trị" để không phải
 * kéo ngang. Dùng lại đúng định nghĩa cột của bảng nên không phải viết giao diện riêng cho điện thoại.
 */
export default function ResponsiveTable<T extends object>(props: Props<T>) {
  return <ResponsiveTableImpl {...(props as unknown as Props<Row>)} />
}

const columnKey = (c: TableColumnType<Row>, i: number) =>
  String(c.key ?? (Array.isArray(c.dataIndex) ? c.dataIndex.join('.') : c.dataIndex) ?? i)

function cellValue(row: Row, c: TableColumnType<Row>, index: number): ReactNode {
  const path = (Array.isArray(c.dataIndex) ? c.dataIndex : c.dataIndex !== undefined ? [c.dataIndex] : []) as (string | number)[]
  let raw: unknown = row
  for (const k of path) raw = (raw as Row | undefined)?.[k]
  if (!c.render) return raw as ReactNode
  const out = c.render(raw, row, index) as unknown
  // render có thể trả về { children, props } (gộp ô); chỉ lấy phần nội dung
  if (out && typeof out === 'object' && 'children' in out && 'props' in out) return (out as { children: ReactNode }).children
  return out as ReactNode
}

function ResponsiveTableImpl({ table, cardTitle, cardExtra, actionsKey = 'actions', hideInCard = [], emptyText, emptyAction }: Props<Row>) {
  const screens = Grid.useBreakpoint()
  const mobile = screens.md === false
  const empty = <EmptyState description={emptyText} action={emptyAction} />

  if (!mobile) {
    return <Table<Row> {...table} locale={{ emptyText: empty, ...table.locale }} />
  }

  const rows = (table.dataSource ?? []) as Row[]
  const columns = (table.columns ?? []) as TableColumnType<Row>[]
  const rowKey = (row: Row, i: number): Key =>
    typeof table.rowKey === 'function' ? table.rowKey(row, i) : ((row[(table.rowKey as string) ?? 'key'] as Key) ?? i)

  if (table.loading && rows.length === 0) return <Skeleton active paragraph={{ rows: 6 }} />
  if (rows.length === 0) return empty

  const paging = table.pagination === false ? undefined : table.pagination
  return (
    <MobileCards
      rows={rows}
      pageSize={paging?.pageSize ?? 10}
      current={paging?.current}
      total={paging?.total}
      onPageChange={paging?.onChange}
      render={(row, i) => (
        <Card key={rowKey(row, i)} size="small" className="data-card">
          <div className="data-card-title">
            <span style={{ minWidth: 0 }}>{cardTitle(row)}</span>
            {cardExtra?.(row)}
          </div>
          {columns.map((c, ci) => {
            const k = columnKey(c, ci)
            if (k === actionsKey || hideInCard.includes(k)) return null
            const value = cellValue(row, c, i)
            if (value === null || value === undefined || value === '') return null
            return (
              <div key={k} className="data-card-row">
                <span>{c.title as ReactNode}</span>
                <span>{value}</span>
              </div>
            )
          })}
          {columns.map((c, ci) =>
            columnKey(c, ci) === actionsKey ? (
              <div key="actions" className="data-card-actions">
                {cellValue(row, c, i)}
              </div>
            ) : null,
          )}
        </Card>
      )}
    />
  )
}

/** Danh sách thẻ có phân trang; tự giữ trang khi danh sách không được phân trang từ máy chủ. */
function MobileCards({
  rows,
  pageSize,
  current,
  total,
  onPageChange,
  render,
}: {
  rows: Row[]
  pageSize: number
  current?: number
  total?: number
  onPageChange?: (page: number, pageSize: number) => void
  render: (row: Row, index: number) => ReactNode
}) {
  const [own, setOwn] = useState(1)
  // Có total và current từ ngoài = phân trang phía máy chủ: rows đã là đúng một trang
  const serverPaged = total !== undefined && current !== undefined
  const page = current ?? own
  const shown = serverPaged ? rows : rows.slice((page - 1) * pageSize, page * pageSize)
  const count = serverPaged ? total : rows.length
  return (
    <>
      <div className="data-card-list">{shown.map((r, i) => render(r, i))}</div>
      {count > pageSize && (
        <Pagination
          size="small"
          align="center"
          className="mt-4"
          current={page}
          pageSize={pageSize}
          total={count}
          showSizeChanger={false}
          onChange={(p, s) => {
            setOwn(p)
            onPageChange?.(p, s)
          }}
        />
      )}
    </>
  )
}
