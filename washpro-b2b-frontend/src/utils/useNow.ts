import { useEffect, useState } from 'react'

/**
 * Thời điểm hiện tại (ms), tự cập nhật mỗi `intervalMs` (mặc định 1 phút).
 * Dùng cho nhãn tương đối như "Còn 3 giờ" / "Trễ 2 giờ" để nhãn đổi theo thời gian khi trang mở lâu,
 * thay vì gọi Date.now() lúc render (kết quả không ổn định và không tự cập nhật).
 */
export function useNow(intervalMs = 60_000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])
  return now
}
