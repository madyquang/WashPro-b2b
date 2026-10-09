import type { ReactNode } from 'react'

interface Props {
  title: ReactNode
  description?: ReactNode
  /** Nút thao tác; nút chính (primary) đặt cuối để nằm sát mép phải. */
  actions?: ReactNode
}

/** Đầu trang dùng chung: tiêu đề h1 (mỗi trang một lần) + mô tả ngắn + thao tác, tự xuống dòng trên điện thoại. */
export default function PageHeader({ title, description, actions }: Props) {
  return (
    <header className="page-header">
      <div className="page-header-text">
        <h1 className="page-header-title">{title}</h1>
        {description && <p className="page-header-desc">{description}</p>}
      </div>
      {actions && <div className="page-header-actions">{actions}</div>}
    </header>
  )
}
