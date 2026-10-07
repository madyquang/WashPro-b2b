import { CUSTOMER_TYPE_LABELS, paymentTermLabel, type Order, type Statement } from '../types'
import { catalogLabel, formatQuantity, groupByCategory } from './catalog'
import { formatCurrency, formatDate, formatDateTime } from './format'

const SHOP_NAME = 'THANHXUAN'

const escapeHtml = (value: string) =>
  value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** Mở cửa sổ in phiếu giao nhận đồ giặt (để hai bên ký xác nhận). */
export function printDeliverySlip(order: Order) {
  const win = window.open('', '_blank', 'width=800,height=900')
  if (!win) return false

  // Nhóm theo loại đồ; cột "Kiểm khi giao" để trống cho khách đếm lại lúc nhận đồ sạch
  const rows = groupByCategory(order.items)
    .map(
      (g) =>
        `<tr class="group"><td colspan="5">${escapeHtml(g.label)}</td></tr>` +
        g.items
          .map(
            (i) =>
              `<tr><td>${escapeHtml(catalogLabel(i))}</td><td class="r">${escapeHtml(formatQuantity(i.quantity, i.unit))}</td><td></td><td class="r">${formatCurrency(i.unitPrice)}</td><td class="r">${formatCurrency(i.lineTotal)}</td></tr>`,
          )
          .join(''),
    )
    .join('')

  win.document.write(`<!doctype html><html lang="vi"><head><meta charset="utf-8" />
<title>Phiếu giao nhận #${order.id}</title>
<style>
  body{font-family:Arial,sans-serif;margin:32px;color:#222}
  h1{text-align:center;margin:0 0 4px} .sub{text-align:center;color:#666;margin-bottom:24px}
  table{border-collapse:collapse;width:100%;margin:16px 0} th,td{border:1px solid #999;padding:6px 8px;text-align:left}
  tr.group td{background:#f2f2f2;font-weight:bold}
  .shop{text-align:center;font-weight:bold;letter-spacing:2px;margin-bottom:8px}
  .r{text-align:right} .info p{margin:4px 0} .sign{display:flex;justify-content:space-between;margin-top:64px;text-align:center}
  .sign div{width:45%} @media print{button{display:none}}
</style></head><body>
<div class="shop">${SHOP_NAME}</div>
<h1>PHIẾU GIAO NHẬN ĐỒ GIẶT</h1>
<div class="sub">Đơn #${order.id} · ${escapeHtml(formatDateTime(order.createdAt))}</div>
<div class="info">
  <p><b>Khách hàng:</b> ${escapeHtml(order.restaurantName)} (${escapeHtml(CUSTOMER_TYPE_LABELS[order.customerType])})</p>
  <p><b>Tài xế:</b> ${escapeHtml(order.driverName)}</p>
  <p><b>Số túi / bao:</b> ${order.totalBags}</p>
  ${order.dueAt ? `<p><b>Hạn trả:</b> ${escapeHtml(formatDateTime(order.dueAt))}</p>` : ''}
  ${order.description ? `<p><b>Ghi chú:</b><br/>${escapeHtml(order.description).replace(/\n/g, '<br/>')}</p>` : ''}
</div>
${
  order.items.length > 0
    ? `<table><thead><tr><th>Loại đồ</th><th class="r">Số lượng</th><th>Kiểm khi giao</th><th class="r">Đơn giá</th><th class="r">Thành tiền</th></tr></thead><tbody>${rows}</tbody>
<tfoot><tr><td colspan="4" class="r"><b>Tổng cộng</b></td><td class="r"><b>${formatCurrency(order.totalAmount)}</b></td></tr></tfoot></table>`
    : '<p><i>Chưa có chi tiết đồ giặt.</i></p>'
}
<div class="sign"><div><b>Đại diện khách hàng</b><br/>(Ký, ghi rõ họ tên)</div><div><b>Tài xế / xưởng giặt</b><br/>(Ký, ghi rõ họ tên)</div></div>
<script>window.onload=function(){window.print()}</script>
</body></html>`)
  win.document.close()
  return true
}

/** In sao kê công nợ tháng để gửi khách đối chiếu. */
export function printStatement(s: Statement) {
  const win = window.open('', '_blank', 'width=800,height=900')
  if (!win) return false
  const [y, m] = s.month.split('-')

  const orderRows = s.orders
    .map(
      (o) =>
        `<tr><td>#${o.id}</td><td>${escapeHtml(formatDateTime(o.completedAt))}</td><td>${escapeHtml(formatDate(o.paymentDue))}</td><td class="r">${formatCurrency(o.amount)}</td></tr>`,
    )
    .join('')
  const paymentRows = s.payments
    .map(
      (p) =>
        `<tr><td>${escapeHtml(formatDate(p.paidDate))}</td><td>${escapeHtml(p.note ?? '')}</td><td class="r">${formatCurrency(p.amount)}</td></tr>`,
    )
    .join('')

  win.document.write(`<!doctype html><html lang="vi"><head><meta charset="utf-8" />
<title>Sao kê ${escapeHtml(s.restaurantName)} ${m}/${y}</title>
<style>
  body{font-family:Arial,sans-serif;margin:32px;color:#222}
  h1{text-align:center;margin:0 0 4px} .sub{text-align:center;color:#666;margin-bottom:24px}
  .shop{text-align:center;font-weight:bold;letter-spacing:2px;margin-bottom:8px}
  table{border-collapse:collapse;width:100%;margin:8px 0 20px} th,td{border:1px solid #999;padding:6px 8px;text-align:left}
  .r{text-align:right} .info p{margin:4px 0} h3{margin:16px 0 4px}
  .sum td{font-weight:bold} .sign{display:flex;justify-content:space-between;margin-top:56px;text-align:center} .sign div{width:45%}
</style></head><body>
<div class="shop">${SHOP_NAME}</div>
<h1>SAO KÊ CÔNG NỢ</h1>
<div class="sub">Tháng ${m}/${y}</div>
<div class="info">
  <p><b>Khách hàng:</b> ${escapeHtml(s.restaurantName)} (${escapeHtml(CUSTOMER_TYPE_LABELS[s.customerType])})</p>
  ${s.address ? `<p><b>Địa chỉ:</b> ${escapeHtml(s.address)}</p>` : ''}
  <p><b>Kỳ thanh toán:</b> ${escapeHtml(paymentTermLabel(s.paymentTermDays))}</p>
</div>
<table><tbody>
  <tr><td>Nợ đầu kỳ (01/${m}/${y})</td><td class="r">${formatCurrency(s.openingBalance)}</td></tr>
  <tr><td>Phát sinh trong tháng (${s.orders.length} đơn)</td><td class="r">${formatCurrency(s.totalBilled)}</td></tr>
  <tr><td>Đã thanh toán trong tháng</td><td class="r">${formatCurrency(s.totalPaid)}</td></tr>
  <tr class="sum"><td>Nợ cuối kỳ</td><td class="r">${formatCurrency(s.closingBalance)}</td></tr>
</tbody></table>
<h3>Chi tiết đơn hoàn thành</h3>
${s.orders.length ? `<table><thead><tr><th>Mã đơn</th><th>Hoàn thành</th><th>Hạn thanh toán</th><th class="r">Số tiền</th></tr></thead><tbody>${orderRows}</tbody></table>` : '<p><i>Không có.</i></p>'}
<h3>Các khoản đã thanh toán</h3>
${s.payments.length ? `<table><thead><tr><th>Ngày</th><th>Ghi chú</th><th class="r">Số tiền</th></tr></thead><tbody>${paymentRows}</tbody></table>` : '<p><i>Không có.</i></p>'}
<div class="sign"><div><b>Đại diện khách hàng</b><br/>(Ký, ghi rõ họ tên)</div><div><b>Kế toán ${SHOP_NAME}</b><br/>(Ký, ghi rõ họ tên)</div></div>
<script>window.onload=function(){window.print()}</script>
</body></html>`)
  win.document.close()
  return true
}
