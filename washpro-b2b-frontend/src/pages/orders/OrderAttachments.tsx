import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Empty, Image, Popconfirm, Segmented, Skeleton, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import axiosClient from '../../api/axiosClient'
import PhotoPicker, { type PickedPhoto } from '../../components/PhotoPicker'
import SignaturePad from '../../components/SignaturePad'
import { uploadAttachments } from '../../utils/attachments'
import { getSession } from '../../utils/auth'
import { formatDateTime, getErrorMessage } from '../../utils/format'
import { message } from '../../utils/message'
import { useNow } from '../../utils/useNow'
import { ATTACHMENT_LABELS, type AttachmentKind, type Order, type OrderAttachment, type Role } from '../../types'

/** Đến khi giặt xong: ảnh / chữ ký thuộc lúc lấy đồ; sau đó thuộc lúc giao trả. */
const PICKUP_STAGE = ['PICKUP_PENDING', 'PICKUP_ASSIGNED', 'RECEIVED', 'WASHING']
/** Người tải lên tự xóa được ảnh của mình trong 24 giờ (khớp backend). */
const SELF_DELETE_MS = 24 * 3600 * 1000

/** Loại được thêm theo vai trò và giai đoạn của đơn (backend vẫn kiểm tra lại theo vai trò). */
function kindsFor(role: Role, order: Order): AttachmentKind[] {
  const pickupStage = PICKUP_STAGE.includes(order.status)
  if (role === 'WORKSHOP_ADMIN') {
    return pickupStage
      ? ['PICKUP_PHOTO', 'PICKUP_SIGNATURE']
      : ['DELIVERY_PHOTO', 'DELIVERY_SIGNATURE', 'ISSUE_PHOTO', 'PICKUP_PHOTO']
  }
  if (role === 'DRIVER') return pickupStage ? ['PICKUP_PHOTO', 'PICKUP_SIGNATURE'] : ['DELIVERY_PHOTO', 'DELIVERY_SIGNATURE']
  if (role === 'CUSTOMER_STAFF' || role === 'CUSTOMER_MANAGER') return pickupStage ? [] : ['ISSUE_PHOTO']
  return []
}

const isSignature = (k: AttachmentKind) => k.endsWith('_SIGNATURE')

/** Ảnh & chữ ký đính kèm đơn (trong ngăn chi tiết đơn). */
export default function OrderAttachments({ order }: { order: Order }) {
  const session = getSession()
  const now = useNow()
  const [items, setItems] = useState<OrderAttachment[] | null>(null)
  const [urls, setUrls] = useState<Record<number, string>>({})
  /** URL tạm đang dùng, để thu hồi khi tải lại / đóng ngăn chi tiết. */
  const liveUrls = useRef<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const kinds = session ? kindsFor(session.role, order) : []
  const [kind, setKind] = useState<AttachmentKind | undefined>(kinds[0])
  const [photos, setPhotos] = useState<PickedPhoto[]>([])
  const [signature, setSignature] = useState<Blob | null>(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      const { data } = await axiosClient.get<OrderAttachment[]>(`/orders/${order.id}/attachments`)
      setItems(data)
      // Ảnh cần token nên tải bằng axios rồi hiển thị qua URL tạm
      const entries = await Promise.all(
        data.map(async (a) => {
          try {
            const res = await axiosClient.get<Blob>(`/attachments/${a.id}/file`, { responseType: 'blob' })
            return [a.id, URL.createObjectURL(res.data)] as const
          } catch {
            return [a.id, ''] as const
          }
        }),
      )
      liveUrls.current.forEach((u) => u && URL.revokeObjectURL(u))
      liveUrls.current = entries.map(([, u]) => u)
      setUrls(Object.fromEntries(entries))
    } catch (e) {
      setError(getErrorMessage(e, 'Không tải được ảnh đính kèm'))
      setItems([])
    }
  }, [order.id])

  useEffect(() => {
    load()
  }, [load])

  // Đóng ngăn chi tiết: thu hồi URL ảnh
  useEffect(() => () => liveUrls.current.forEach((u) => u && URL.revokeObjectURL(u)), [])

  const resetForm = () => {
    setAdding(false)
    setPhotos([])
    setSignature(null)
  }

  const save = async () => {
    if (!kind) return
    const files = isSignature(kind)
      ? signature
        ? [{ kind, blob: signature }]
        : []
      : photos.map((p) => ({ kind, blob: p.blob }))
    if (files.length === 0) return
    setSaving(true)
    const failed = await uploadAttachments(order.id, files)
    setSaving(false)
    if (failed) message.error(`Không tải được ${failed}/${files.length} ảnh, vui lòng thử lại`)
    else message.success(isSignature(kind) ? 'Đã lưu chữ ký' : 'Đã lưu ảnh')
    resetForm()
    load()
  }

  const remove = async (a: OrderAttachment) => {
    try {
      await axiosClient.delete(`/attachments/${a.id}`)
      message.success('Đã xóa ảnh')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const canDelete = (a: OrderAttachment) =>
    session?.role === 'WORKSHOP_ADMIN' ||
    (a.uploadedBy === session?.username && now - new Date(a.createdAt).getTime() < SELF_DELETE_MS)

  return (
    <div className="order-attachments">
      {items === null && <Skeleton.Image active />}
      {error && <Typography.Text type="danger">{error}</Typography.Text>}
      {items && items.length === 0 && !adding && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có ảnh hay chữ ký" />
      )}
      {items && items.length > 0 && (
        <Image.PreviewGroup>
          <div className="attachment-grid">
            {items.map((a) => (
              <figure key={a.id} className="attachment-card">
                {urls[a.id] ? (
                  <Image src={urls[a.id]} alt={a.kindLabel} width="100%" height={110} style={{ objectFit: 'cover' }} />
                ) : (
                  <div className="attachment-missing">Không tải được ảnh</div>
                )}
                <figcaption>
                  <strong>{a.kindLabel}</strong>
                  <span>
                    {a.uploadedBy} · {formatDateTime(a.createdAt)}
                  </span>
                </figcaption>
                {canDelete(a) && (
                  <Popconfirm title="Xóa ảnh này?" okText="Xóa" cancelText="Không" onConfirm={() => remove(a)}>
                    <Button size="small" type="text" danger icon={<DeleteOutlined />} aria-label={`Xóa ${a.kindLabel}`} />
                  </Popconfirm>
                )}
              </figure>
            ))}
          </div>
        </Image.PreviewGroup>
      )}

      {kinds.length > 0 && !adding && (
        <Button icon={<PlusOutlined />} onClick={() => setAdding(true)} style={{ marginTop: 8 }}>
          Thêm ảnh / chữ ký
        </Button>
      )}
      {adding && kind && (
        <div className="attachment-form">
          <Segmented<AttachmentKind>
            block
            value={kind}
            onChange={(k) => {
              setKind(k)
              setPhotos([])
              setSignature(null)
            }}
            options={kinds.map((k) => ({ value: k, label: ATTACHMENT_LABELS[k] }))}
          />
          {isSignature(kind) ? (
            <SignaturePad onChange={setSignature} />
          ) : (
            <PhotoPicker value={photos} onChange={setPhotos} label="Chọn / chụp ảnh" />
          )}
          <div className="attachment-form-actions">
            <Button onClick={resetForm}>Hủy</Button>
            <Button
              type="primary"
              loading={saving}
              disabled={isSignature(kind) ? !signature : photos.length === 0}
              onClick={save}
            >
              Lưu
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
