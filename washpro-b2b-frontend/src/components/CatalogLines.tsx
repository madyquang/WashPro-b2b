import { Button, Form, Input, InputNumber, Select } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { CUSTOM_ITEM, catalogOptions, lineUnit, type LineValue } from '../utils/catalog'
import { moneyFormatter, moneyParser } from '../utils/format'
import { UNIT_META, type CatalogItem, type PriceUnit } from '../types'

interface Props {
  /** Tên trường Form.List trong form cha. */
  name: string
  catalog: CatalogItem[]
  loading?: boolean
  /** Hiện ô đơn giá (chỉ cho Kế toán / Quản lý xưởng). */
  withPrice?: boolean
  /** Giá áp dụng cho khách của đơn, để tự điền khi chọn loại đồ. */
  priceOf?: (servicePriceId: number) => number | undefined
  /** Số dòng tối thiểu (không cho xóa dòng cuối). */
  minLines?: number
  addLabel?: string
}

/** Danh sách dòng đồ giặt: chọn loại đồ theo nhóm, số lượng theo đơn vị (kg được nhập số lẻ). */
export default function CatalogLines({ name, catalog, loading, withPrice, priceOf, minLines = 0, addLabel = 'Thêm loại đồ' }: Props) {
  const form = Form.useFormInstance()
  const lines = (Form.useWatch(name, form) as LineValue[] | undefined) ?? []

  const options = [
    ...catalogOptions(catalog),
    { label: 'Khác', title: 'Khác', options: [{ value: CUSTOM_ITEM, label: '＋ Đồ ngoài bảng giá (nhập tên)' }] },
  ]

  const onPick = (index: number, id: number) => {
    if (!withPrice || !priceOf || id === CUSTOM_ITEM) return
    const price = priceOf(id)
    if (price !== undefined) form.setFieldValue([name, index, 'unitPrice'], price)
  }

  return (
    <Form.List name={name}>
      {(fields, { add, remove }) => (
        <>
          {fields.map((field, index) => {
            const line = lines[index]
            const unit = lineUnit(line, catalog)
            const decimal = UNIT_META[unit].decimal
            const custom = line?.servicePriceId === CUSTOM_ITEM
            return (
              // Mỗi dòng tự xuống hàng theo bề rộng: máy tính một hàng, điện thoại loại đồ một hàng + số lượng / giá một hàng
              <div key={field.key} className="line-row">
                <Form.Item name={[field.name, 'servicePriceId']} rules={[{ required: true, message: 'Chọn loại đồ' }]} className="line-item">
                  <Select
                    showSearch={{ optionFilterProp: 'label' }}
                    aria-label={`Loại đồ dòng ${index + 1}`}
                    placeholder="Chọn loại đồ"
                    loading={loading}
                    options={options}
                    popupMatchSelectWidth={false}
                    onChange={(id: number) => onPick(index, id)}
                    notFoundContent="Bảng giá chưa có mục nào"
                  />
                </Form.Item>
                {custom && (
                  <>
                    <Form.Item name={[field.name, 'itemName']} rules={[{ required: true, whitespace: true, message: 'Nhập tên đồ' }]} className="line-name">
                      <Input aria-label={`Tên đồ dòng ${index + 1}`} placeholder="Tên đồ (VD: Rèm cửa)" maxLength={100} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'unit']} initialValue="PIECE" className="line-unit">
                      <Select
                        aria-label={`Đơn vị dòng ${index + 1}`}
                        options={(Object.keys(UNIT_META) as PriceUnit[]).map((u) => ({ value: u, label: UNIT_META[u].label }))}
                      />
                    </Form.Item>
                  </>
                )}
                <Form.Item name={[field.name, 'quantity']} rules={[{ required: true, message: 'Nhập SL' }]} className="line-qty">
                  <InputNumber
                    aria-label={`Số lượng dòng ${index + 1}`}
                    min={decimal ? 0.01 : 1}
                    max={100000}
                    precision={decimal ? 2 : 0}
                    step={decimal ? 0.5 : 1}
                    placeholder="SL"
                    inputMode={decimal ? 'decimal' : 'numeric'}
                    suffix={UNIT_META[unit].label}
                    style={{ width: '100%' }}
                  />
                </Form.Item>
                {withPrice && (
                  <Form.Item name={[field.name, 'unitPrice']} className="line-price">
                    <InputNumber
                      aria-label={`Đơn giá dòng ${index + 1}`}
                      min={0}
                      precision={0}
                      placeholder="Đơn giá (đ)"
                      style={{ width: '100%' }}
                      formatter={moneyFormatter}
                      parser={moneyParser}
                      suffix={`/${UNIT_META[unit].label}`}
                    />
                  </Form.Item>
                )}
                {fields.length > minLines && (
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    className="line-remove"
                    aria-label={`Xóa dòng ${index + 1}`}
                    title="Xóa dòng"
                    onClick={() => remove(field.name)}
                  />
                )}
              </div>
            )
          })}
          <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ quantity: 1 })} block>
            {addLabel}
          </Button>
        </>
      )}
    </Form.List>
  )
}
