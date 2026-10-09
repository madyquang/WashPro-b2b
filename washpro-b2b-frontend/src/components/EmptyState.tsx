import type { ReactNode } from 'react'
import { Button, Empty } from 'antd'

interface Props {
  description: ReactNode
  /** Một dòng chữ gọn (dùng trong các khối trang chủ) thay cho hình minh họa lớn. */
  compact?: boolean
  /** Bước tiếp theo hợp lệ cho vai trò hiện tại (nếu có). */
  action?: { label: string; icon?: ReactNode; onClick: () => void }
}

/** Danh sách rỗng: giải thích ngắn + nút đi tiếp, để người dùng không bị kẹt. */
export default function EmptyState({ description, action, compact }: Props) {
  if (compact) {
    return (
      <div className="empty-compact">
        <span>{description}</span>
        {action && (
          <Button size="small" icon={action.icon} onClick={action.onClick}>
            {action.label}
          </Button>
        )}
      </div>
    )
  }
  return (
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} className="empty-state">
      {action && (
        <Button type="primary" icon={action.icon} onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </Empty>
  )
}
