import { useEffect, useRef, useState } from 'react'
import { Button } from 'antd'
import { CameraOutlined, CloseOutlined } from '@ant-design/icons'
import { compressImage } from '../utils/attachments'

export interface PickedPhoto {
  id: number
  blob: Blob
  /** URL tạm để xem trước (thu hồi khi bỏ ảnh / đóng). */
  url: string
}

interface Props {
  value: PickedPhoto[]
  onChange: (photos: PickedPhoto[]) => void
  max?: number
  label?: string
}

let nextId = 1

/** Chọn / chụp ảnh (không bắt buộc), thu nhỏ ngay trên máy trước khi gửi, xem trước và bỏ từng ảnh. */
export default function PhotoPicker({ value, onChange, max = 5, label = 'Thêm ảnh' }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)

  // Đóng form: thu hồi URL xem trước còn lại
  const current = useRef(value)
  useEffect(() => {
    current.current = value
  }, [value])
  useEffect(() => () => current.current.forEach((p) => URL.revokeObjectURL(p.url)), [])

  const pick = async (files: FileList | null) => {
    if (!files?.length) return
    setBusy(true)
    const room = Math.max(0, max - value.length)
    const added: PickedPhoto[] = []
    for (const f of Array.from(files).slice(0, room)) {
      const blob = await compressImage(f)
      added.push({ id: nextId++, blob, url: URL.createObjectURL(blob) })
    }
    setBusy(false)
    onChange([...value, ...added])
    if (input.current) input.current.value = ''
  }

  const remove = (p: PickedPhoto) => {
    URL.revokeObjectURL(p.url)
    onChange(value.filter((x) => x.id !== p.id))
  }

  return (
    <div className="photo-picker">
      {value.map((p) => (
        <div key={p.id} className="photo-thumb">
          <img src={p.url} alt="Ảnh đã chọn" />
          <button type="button" aria-label="Bỏ ảnh này" onClick={() => remove(p)}>
            <CloseOutlined />
          </button>
        </div>
      ))}
      {value.length < max && (
        <Button icon={<CameraOutlined />} loading={busy} onClick={() => input.current?.click()} className="photo-add">
          {label}
        </Button>
      )}
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        hidden
        aria-label={label}
        onChange={(e) => pick(e.target.files)}
      />
    </div>
  )
}
