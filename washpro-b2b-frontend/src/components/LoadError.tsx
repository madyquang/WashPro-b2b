import { Alert, Button } from 'antd'

/** Lỗi tải dữ liệu của trang: nêu lỗi + nút "Thử lại" (không hiện gì khi không có lỗi). */
export default function LoadError({ error, onRetry }: { error: string | null; onRetry?: () => void }) {
  if (!error) return null
  return (
    <Alert
      type="error"
      showIcon
      className="mb-4"
      title={error}
      action={
        onRetry && (
          <Button size="small" onClick={onRetry}>
            Thử lại
          </Button>
        )
      }
    />
  )
}
