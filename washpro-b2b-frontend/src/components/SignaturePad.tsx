import { useEffect, useRef, useState } from 'react'
import { Button } from 'antd'

interface Props {
  /** Ảnh chữ ký (PNG) sau mỗi nét, null khi chưa ký / đã xóa. */
  onChange: (signature: Blob | null) => void
  label?: string
}

const HEIGHT = 160

/** Khung ký tên bằng ngón tay / chuột / bút. */
export default function SignaturePad({ onChange, label = 'Khách ký tên' }: Props) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const [signed, setSigned] = useState(false)

  const clear = () => {
    const c = canvas.current
    const ctx = c?.getContext('2d')
    if (!c || !ctx) return
    // Nền trắng để ảnh chữ ký in / xem trên nền tối vẫn rõ
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, c.width, c.height)
    setSigned(false)
    onChange(null)
  }

  useEffect(() => {
    const c = canvas.current
    if (!c) return
    // Độ nét theo mật độ điểm ảnh của màn hình
    const ratio = window.devicePixelRatio || 1
    c.width = c.clientWidth * ratio
    c.height = HEIGHT * ratio
    const ctx = c.getContext('2d')
    if (!ctx) return
    ctx.scale(ratio, ratio)
    ctx.lineWidth = 2.2
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.strokeStyle = '#0b2545'
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, c.width, c.height)
  }, [])

  const point = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top }
  }

  const start = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const ctx = canvas.current?.getContext('2d')
    if (!ctx) return
    e.currentTarget.setPointerCapture(e.pointerId)
    drawing.current = true
    const p = point(e)
    ctx.beginPath()
    ctx.moveTo(p.x, p.y)
    ctx.lineTo(p.x + 0.1, p.y + 0.1)
    ctx.stroke()
  }

  const move = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return
    const ctx = canvas.current?.getContext('2d')
    if (!ctx) return
    const p = point(e)
    ctx.lineTo(p.x, p.y)
    ctx.stroke()
  }

  const end = () => {
    if (!drawing.current) return
    drawing.current = false
    setSigned(true)
    canvas.current?.toBlob((b) => onChange(b), 'image/png')
  }

  return (
    <div className="signature-pad">
      <canvas
        ref={canvas}
        style={{ height: HEIGHT }}
        role="img"
        aria-label={label}
        onPointerDown={start}
        onPointerMove={move}
        onPointerUp={end}
        onPointerLeave={end}
      />
      <div className="signature-actions">
        <span className="signature-hint">{signed ? 'Đã ký' : 'Ký vào khung trên'}</span>
        <Button size="small" onClick={clear} disabled={!signed}>
          Ký lại
        </Button>
      </div>
    </div>
  )
}
