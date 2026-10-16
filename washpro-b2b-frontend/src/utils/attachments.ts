import axiosClient from '../api/axiosClient'
import type { AttachmentKind } from '../types'

/** Ảnh chụp điện thoại thường 3–8 MB: thu nhỏ còn cạnh dài tối đa 1600px, JPEG ~80% (thường 200–500 KB). */
const MAX_SIDE = 1600
const QUALITY = 0.8

export async function compressImage(file: Blob): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) return file
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', QUALITY))
    return blob && blob.size < file.size ? blob : file
  } catch {
    // Trình duyệt không đọc được ảnh (định dạng lạ): gửi nguyên, máy chủ sẽ kiểm tra
    return file
  }
}

export interface PendingAttachment {
  kind: AttachmentKind
  blob: Blob
}

/** Tải lần lượt các ảnh / chữ ký lên một đơn; trả về số file không tải được. */
export async function uploadAttachments(orderId: number, files: PendingAttachment[]): Promise<number> {
  let failed = 0
  for (const f of files) {
    const form = new FormData()
    form.append('kind', f.kind)
    form.append('file', f.blob, f.blob.type === 'image/png' ? 'chu-ky.png' : 'anh.jpg')
    try {
      await axiosClient.post(`/orders/${orderId}/attachments`, form)
    } catch {
      failed++
    }
  }
  return failed
}
