/**
 * Kiểm thử đầu-cuối (E2E) qua giao diện thật: đăng nhập từng vai trò và bấm hết các luồng nghiệp vụ.
 * Ghi dữ liệu vào CSDL (gắn nhãn "E2E-..."), nên chỉ chạy trên CSDL thử / dữ liệu demo.
 *
 * Cần: backend (8080) + frontend (5173) đang chạy, dữ liệu demo (DEMO_DATA=true), mật khẩu chung của tài khoản demo.
 * Chạy:  npm run test:e2e                  (tất cả module)
 *        npm run test:e2e -- auth,pickup    (chọn module: auth, pickup, driver, confirm, admin-orders, pwa,
 *                                            receipt, finance, admin-manage)
 * Biến môi trường: E2E_APP_URL, E2E_API_URL, E2E_PASSWORD, E2E_BROWSER (msedge | chrome | chromium), E2E_OUT.
 * Kết quả: in PASS/FAIL từng bước; ảnh chụp bước lỗi trong e2e/results/. Mã thoát 1 nếu có bước lỗi hoặc lỗi API/JS.
 */
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const out = process.env.E2E_OUT ?? fileURLToPath(new URL('./results', import.meta.url))
mkdirSync(out, { recursive: true })
const only = process.argv[2]?.split(',')
const APP = process.env.E2E_APP_URL ?? 'http://localhost:5173'
const API = process.env.E2E_API_URL ?? 'http://localhost:8080/api'
const PW = process.env.E2E_PASSWORD ?? '11111111'
// Windows dùng sẵn Edge; nơi khác (CI Linux) dùng Chromium của Playwright (npx playwright-core install chromium)
const CHANNEL = process.env.E2E_BROWSER ?? (process.platform === 'win32' ? 'msedge' : 'chromium')
const TAG = `E2E-${Date.now().toString(36)}`
const MOBILE = { width: 390, height: 844 }
const DESKTOP = { width: 1440, height: 900 }
/** Người dùng ở Việt Nam: trình duyệt cùng múi giờ với máy chủ (CI chạy giờ UTC, lệch ngày từ 0h đến 7h sáng). */
const TZ = 'Asia/Ho_Chi_Minh'

const browser = await chromium.launch(CHANNEL === 'chromium' ? { headless: true } : { channel: CHANNEL, headless: true })
const results = []
const problems = []

// ---------- tiện ích ----------
const tokens = {}
async function api(user, path, init = {}) {
  if (!tokens[user]) {
    const r = await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: user, password: PW }) })
    tokens[user] = (await r.json()).token
  }
  const r = await fetch(API + path, { ...init, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[user]}`, ...(init.headers ?? {}) } })
  return r.status === 204 ? null : r.json()
}
/** Ảnh PNG nhỏ để thử đính kèm ảnh. */
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64')
/** Ký vào khung chữ ký bằng chuột. */
async function sign(page, scope) {
  const canvas = scope.locator('.signature-pad canvas')
  // hover tự cuộn tới và kiểm tra điểm đặt chuột không bị phần tử khác (chân modal...) che
  await canvas.hover({ position: { x: 20, y: 40 } })
  const box = await canvas.boundingBox()
  await page.mouse.down()
  await page.mouse.move(box.x + 140, box.y + 100, { steps: 10 })
  await page.mouse.up()
  await scope.getByText('Đã ký').waitFor()
}
/** Tìm đơn theo ghi chú (mô tả) trong các đơn gần nhất. */
async function findOrder(note) {
  const page = await api('admin', '/orders?size=50')
  const o = page.content.find((x) => (x.description ?? '').includes(note))
  if (!o) throw new Error(`không tìm thấy đơn có ghi chú "${note}"`)
  return o
}
/** Mở chuông thông báo, bấm thông báo đầu tiên có tiêu đề khớp. */
async function openNotification(page, title) {
  await page.getByRole('button', { name: /^Thông báo, \d+ chưa đọc$/ }).click()
  const item = page.locator('.notif-item').filter({ hasText: title }).first()
  await item.waitFor()
  await item.click()
}
async function orderStatus(id) {
  return (await api('admin', `/orders/${id}`)).status
}

async function open(user, viewport = DESKTOP, label = user, password = PW) {
  // Chặn service worker ở các bước nghiệp vụ: page.route (giả lập API chậm...) không thấy yêu cầu đi qua service worker.
  // Module pwa kiểm tra riêng service worker.
  const ctx = await browser.newContext({ viewport, acceptDownloads: true, serviceWorkers: 'block', timezoneId: TZ })
  // In nguyên nhân gốc của lỗi React "có thể phục hồi" (React gói lỗi thật vào error.cause)
  await ctx.addInitScript(() => {
    window.addEventListener('error', (e) => {
      const c = e.error && e.error.cause
      if (c) console.error('NGUYEN NHAN GOC: ' + (c.stack || c.message || String(c)).slice(0, 600))
    })
  })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => problems.push(`[${label}] JS: ${e.message}`))
  page.on('console', (m) => {
    if (m.type() === 'error' && !/List.*deprecated|Failed to load resource/.test(m.text())) problems.push(`[${label}] console: ${m.text().slice(0, /NGUYEN NHAN/.test(m.text()) ? 600 : 200)}`)
    if (m.type() === 'warning' && /antd/.test(m.text()) && !/List.*deprecated/.test(m.text())) problems.push(`[${label}] antd warning: ${m.text().slice(0, 160)}`)
  })
  page.on('response', async (r) => {
    if (r.url().includes('/api/') && r.status() >= 400 && !page.__expectError) {
      let body = ''
      try {
        body = (await r.text()).slice(0, 200)
      } catch {}
      problems.push(`[${label}] API ${r.request().method()} ${r.url().replace(/.*\/api/, '')} -> ${r.status()} ${body}`)
    }
  })
  if (user) await login(page, user, password)
  return { ctx, page }
}
async function login(page, user, password = PW) {
  await page.goto(APP + '/login')
  await page.getByLabel('Tên đăng nhập').fill(user)
  await page.getByLabel('Mật khẩu').fill(password)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  await page.waitForURL(APP + '/')
  await page.locator('h1').first().waitFor()
  await page.waitForLoadState('networkidle')
}

async function step(name, fn, page) {
  try {
    await fn()
    results.push(['PASS', name])
    console.log('PASS', name)
  } catch (e) {
    results.push(['FAIL', name, e.message.split('\n')[0]])
    console.log('FAIL', name, '-', e.message.split('\n').slice(0, 6).join(' | '))
    if (page) await page.screenshot({ path: `${out}/FAIL-${name.replace(/[^\w-]+/g, '_')}.png` }).catch(() => {})
  }
}

const toast = (page, text) => page.locator('.ant-message-notice').filter({ hasText: text }).first().waitFor({ timeout: 10000 })
/** Modal đang mở (bỏ qua drawer / modal đã đóng còn nằm trong DOM). */
const modal = (page) => page.locator('.ant-modal-wrap:visible .ant-modal').last()
const drawer = (page) => page.locator('.ant-drawer:visible').last()
async function pick(page, scope, label, text) {
  const box = scope.getByLabel(label, { exact: true })
  const holder = box.locator('xpath=ancestor::div[contains(@class,"ant-select")][1]')
  for (let attempt = 0; attempt < 2; attempt++) {
    await box.click()
    if (text) await box.fill(text)
    // Chờ danh sách tải xong (có mục khớp) rồi mới chọn, như người dùng thật
    const opt = page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: text ?? '' }).first()
    await opt.waitFor()
    await page.waitForTimeout(250) // hiệu ứng mở danh sách xong
    await opt.click()
    if (!text || (await holder.innerText()).toLowerCase().includes(text.toLowerCase())) return
  }
  throw new Error(`không chọn được "${text}" ở ô ${label}`)
}
/** Bấm nút có Popconfirm / Modal.confirm rồi xác nhận. */
async function confirmClick(page, button, okText) {
  await button.click()
  await page.locator('.ant-popover:visible, .ant-modal-confirm:visible').getByRole('button', { name: okText }).click()
}
/** Bảng đơn (máy tính): bấm một thao tác của đơn, kể cả khi nằm trong menu "⋯". */
async function rowAction(page, orderId, label) {
  const row = page.locator('tr').filter({ hasText: `#${orderId}` }).first()
  await row.waitFor() // bảng tải xong mới tìm nút
  const btn = row.getByRole('button', { name: label, exact: true })
  if (await btn.count()) return btn.click()
  await row.getByRole('button', { name: 'Thao tác khác' }).click()
  await page.locator('.ant-dropdown:visible').getByRole('menuitem', { name: label }).click()
}
/** Mở danh sách đơn rồi tìm theo mã "#id" bằng ô tìm kiếm (như người dùng khi danh sách dài). */
async function gotoOrder(page, path, id) {
  await page.goto(APP + path)
  await page.getByLabel('Tìm đơn').fill(`#${id}`)
  await page.locator(`tr:has-text("#${id}"), .order-card:has-text("#${id}")`).first().waitFor()
}
const want = (m) => !only || only.includes(m)
const expect = (cond, msg) => {
  if (!cond) throw new Error(msg)
}

// ============ M1. Đăng nhập ============
if (want('auth')) {
  const { ctx, page } = await open(null, DESKTOP, 'auth')
  await step('auth: sai mật khẩu báo lỗi rõ ràng', async () => {
    await page.goto(APP + '/login')
    page.__expectError = true
    await page.getByLabel('Tên đăng nhập').fill('admin')
    await page.getByLabel('Mật khẩu').fill('sai-mat-khau')
    await page.getByRole('button', { name: 'Đăng nhập' }).click()
    await page.getByText('Tên đăng nhập hoặc mật khẩu không chính xác').waitFor()
    page.__expectError = false
  }, page)
  await step('auth: đăng nhập đúng vào trang chủ', async () => {
    await page.getByLabel('Mật khẩu').fill(PW)
    await page.getByRole('button', { name: 'Đăng nhập' }).click()
    await page.waitForURL(APP + '/')
  }, page)
  await step('auth: đăng xuất về trang đăng nhập', async () => {
    await page.locator('.user-button').click()
    await page.getByRole('menuitem', { name: 'Đăng xuất' }).click()
    await page.waitForURL('**/login')
  }, page)
  await step('auth: đăng xuất khi trang chủ còn đang tải không gây lỗi React', async () => {
    for (const user of ['admin', 'ketoan.mai', 'taixe.tai', 'hoasen.ql']) {
      const before = problems.length
      // Làm chậm dữ liệu trang chủ: dữ liệu về sau khi đã đăng xuất
      await page.route('**/api/dashboard', async (r) => {
        await new Promise((res) => setTimeout(res, 1500))
        await r.continue().catch(() => {})
      })
      await page.goto(APP + '/login')
      await page.getByLabel('Tên đăng nhập').fill(user)
      await page.getByLabel('Mật khẩu').fill(PW)
      await page.getByRole('button', { name: 'Đăng nhập' }).click()
      await page.waitForURL(APP + '/')
      await page.locator('.user-button').click()
      await page.getByRole('menuitem', { name: 'Đăng xuất' }).click()
      await page.waitForURL('**/login')
      await page.waitForTimeout(2500)
      await page.unroute('**/api/dashboard')
      const err = problems.slice(before).find((x) => /NGUYEN NHAN|concurrent rendering|JS:|-> 401/.test(x))
      expect(!err, `${user}: ${err}`)
      expect(!page.url().includes('expired') && (await page.getByText('Phiên đăng nhập đã hết hạn').count()) === 0, `${user}: tự đăng xuất mà báo phiên hết hạn`)
    }
  }, page)
  await step('auth: chưa đăng nhập mở trang trong bị chuyển về /login', async () => {
    await page.goto(APP + '/orders')
    await page.waitForURL('**/login')
  }, page)
  await step('auth: vai trò không đủ quyền mở URL quản trị bị chuyển về trang chủ', async () => {
    await login(page, 'taixe.tai')
    await page.goto(APP + '/accounts')
    await page.waitForURL(APP + '/')
  }, page)
  await ctx.close()
}

// ============ M2. Khách gửi / hủy yêu cầu lấy đồ ============
if (want('pickup')) {
  const { ctx, page } = await open('hoasen.nv', MOBILE, 'nhân viên khách')
  await step('pickup: khách gửi yêu cầu từ trang chủ', async () => {
    await page.getByRole('button', { name: 'Yêu cầu lấy đồ' }).first().click()
    const d = modal(page)
    await d.getByText('Sáng (7h–11h)').click()
    await d.getByLabel('Số túi ước tính (không bắt buộc)').fill('3')
    await d.getByLabel('Ghi chú cho tài xế').fill(`${TAG} lấy ở cửa sau`)
    await d.getByRole('button', { name: 'Gửi yêu cầu' }).click()
    await toast(page, 'Đã gửi yêu cầu')
    await page.getByText(`${TAG} lấy ở cửa sau`).first().waitFor()
  }, page)
  await step('pickup: không cho chọn ngày lấy trong quá khứ', async () => {
    await page.getByRole('button', { name: 'Yêu cầu lấy đồ' }).first().click()
    const min = await modal(page).getByLabel('Ngày lấy').getAttribute('min')
    expect(min && min.length === 10, 'ô ngày không có giới hạn min')
    await modal(page).getByRole('button', { name: 'Hủy' }).click()
  }, page)
  await step('pickup: gửi yêu cầu thứ hai rồi hủy kèm lý do', async () => {
    await page.getByRole('button', { name: 'Yêu cầu lấy đồ' }).first().click()
    const d = modal(page)
    await d.getByLabel('Ghi chú cho tài xế').fill(`${TAG} sẽ hủy`)
    await d.getByRole('button', { name: 'Gửi yêu cầu' }).click()
    await toast(page, 'Đã gửi yêu cầu')
    await page.goto(APP + '/pickups')
    await page.locator('.ant-card').filter({ hasText: `${TAG} sẽ hủy` }).getByRole('button', { name: 'Hủy yêu cầu' }).click()
    await modal(page).getByLabel('Lý do hủy').fill('Test hủy')
    await modal(page).getByRole('button', { name: 'Hủy yêu cầu' }).click()
    await toast(page, 'Đã hủy yêu cầu')
    await page.getByRole('tab', { name: /Đã xong/ }).click()
    await page.locator('.ant-card').filter({ hasText: `${TAG} sẽ hủy` }).getByText('Đã hủy').first().waitFor()
  }, page)
  await ctx.close()
}

// ============ M3. Tài xế nhận việc, tạo đơn ============
if (want('driver')) {
  const { ctx, page } = await open('taixe.tai', MOBILE, 'tài xế')
  const card = () => page.locator('.ant-card').filter({ hasText: `${TAG} lấy ở cửa sau` }).first()
  await step('driver: chuông báo yêu cầu lấy đồ mới, bấm mở trang lấy đồ', async () => {
    await openNotification(page, 'Yêu cầu lấy đồ mới: Khách sạn Hoa Sen')
    await page.waitForURL(/\/pickups/)
  }, page)
  await step('driver: nhận việc ở tab "Chờ nhận"', async () => {
    await page.goto(APP + '/pickups?tab=open')
    await card().getByRole('button', { name: 'Nhận việc' }).click()
    await toast(page, 'Đã nhận việc')
    await page.getByRole('tab', { name: /Việc của tôi/ }).click()
    await card().waitFor()
  }, page)
  await step('driver: việc vừa nhận hiện ở trang chủ "Việc của tôi"', async () => {
    await page.goto(APP + '/')
    await page.locator('.home-section').filter({ hasText: 'Việc của tôi' }).locator('.ant-card').filter({ hasText: `${TAG} lấy ở cửa sau` }).waitFor()
  }, page)
  await step('driver: lộ trình có điểm vừa nhận, sắp lại thứ tự, mở Google Maps nhiều điểm', async () => {
    await page.goto(APP + '/route')
    const stop = page.locator('.route-stop').filter({ hasText: `${TAG} lấy ở cửa sau` })
    await stop.waitFor()
    await stop.getByText('Quận 1').waitFor()
    const href = await page.getByRole('link', { name: /Mở Google Maps/ }).first().getAttribute('href')
    expect(href?.startsWith('https://www.google.com/maps/dir/?') && href.includes('destination='), 'link Maps sai: ' + href)
    if ((await page.locator('.route-stop').count()) > 1) {
      const first = await page.locator('.route-stop').first().getAttribute('data-key')
      await page.locator('.route-stop').first().getByRole('button', { name: /xuống sau/ }).click()
      await page.waitForFunction((f) => document.querySelectorAll('.route-stop')[1]?.dataset.key === f, first, { timeout: 5000 })
      await page.getByRole('button', { name: 'Về thứ tự gợi ý' }).click()
      await page.waitForFunction((f) => document.querySelectorAll('.route-stop')[0]?.dataset.key === f, first, { timeout: 5000 })
    }
  }, page)
  await step('driver: nút Gọi / Chỉ đường đúng liên kết', async () => {
    await page.goto(APP + '/')
    const call = await card().getByRole('link', { name: 'Gọi' }).getAttribute('href')
    const map = await card().getByRole('link', { name: 'Chỉ đường' }).getAttribute('href')
    expect(call?.startsWith('tel:') && map?.includes('google.com/maps'), `href sai: ${call} / ${map}`)
  }, page)
  await step('driver: đã đến, tạo đơn từ yêu cầu (2 loại đồ, kèm ảnh và chữ ký khách)', async () => {
    await card().getByRole('button', { name: 'Đã đến · Tạo đơn' }).click()
    const d = modal(page)
    await d.getByText(`Khách dặn: ${TAG}`).waitFor()
    expect((await d.getByLabel('Số túi / bao đồ').inputValue()) === '3', 'số túi không điền sẵn từ yêu cầu')
    await pick(page, d, 'Loại đồ dòng 1', 'Khăn')
    await d.getByLabel('Số lượng dòng 1').fill('12')
    await d.getByRole('button', { name: 'Thêm loại đồ' }).click()
    await pick(page, d, 'Loại đồ dòng 2', 'Ga')
    await d.getByLabel('Số lượng dòng 2').fill('4')
    await d.getByLabel('Ghi chú').fill(`${TAG} từ yêu cầu`)
    await d.getByText(/Ảnh & chữ ký khách/).click()
    await d.locator('input[type=file]').setInputFiles({ name: 'tui-do.png', mimeType: 'image/png', buffer: PNG })
    await d.locator('.photo-thumb').first().waitFor()
    await sign(page, d)
    await d.getByRole('button', { name: 'Tạo đơn' }).click()
    await toast(page, 'Đã tạo đơn')
    const o = await findOrder(`${TAG} từ yêu cầu`)
    const att = await api('admin', `/orders/${o.id}/attachments`)
    const kinds = att.map((a) => a.kind).sort().join(',')
    expect(kinds === 'PICKUP_PHOTO,PICKUP_SIGNATURE', `ảnh đính kèm sai: ${kinds}`)
    expect(o.status === 'PENDING_CONFIRM' && o.totalBags === 3 && o.items.length === 2, `đơn sai: ${o.status} ${o.totalBags} túi ${o.items.length} dòng`)
    const pk = (await api('admin', '/pickups')).find((p) => (p.note ?? '').includes(`${TAG} lấy ở cửa sau`))
    expect(pk?.status === 'DONE' && pk.orderId === o.id, `yêu cầu lấy đồ chưa gắn đơn: ${pk?.status} ${pk?.orderId}`)
  }, page)
  for (const [note, qty] of [[`${TAG} đơn lẻ`, '5'], [`${TAG} sẽ hủy đơn`, '2']]) {
    await step(`driver: tạo đơn không theo yêu cầu (${note})`, async () => {
      await page.goto(APP + '/')
      await page.getByRole('button', { name: 'Tạo đơn không theo yêu cầu' }).click()
      const d = modal(page)
      await pick(page, d, 'Khách hàng', 'Hoa Sen')
      await pick(page, d, 'Loại đồ dòng 1', 'Khăn')
      await d.getByLabel('Số lượng dòng 1').fill(qty)
      await d.getByLabel('Ghi chú').fill(note)
      await d.getByRole('button', { name: 'Tạo đơn' }).click()
      await toast(page, 'Đã tạo đơn')
      await findOrder(note)
    }, page)
  }
  await step('driver: không nhập loại đồ thì báo lỗi ngay dưới ô', async () => {
    await page.getByRole('button', { name: 'Tạo đơn không theo yêu cầu' }).click()
    const d = modal(page)
    await d.getByRole('button', { name: 'Tạo đơn' }).click()
    await d.getByText('Chọn loại đồ').first().waitFor()
    await d.getByRole('button', { name: 'Hủy' }).click()
  }, page)
  await ctx.close()
}

// ============ M4. Khách xác nhận / báo sai lệch ============
if (want('confirm')) {
  const { ctx, page } = await open('hoasen.nv', MOBILE, 'nhân viên khách')
  const cardOf = (id) => page.locator('.order-card').filter({ hasText: `Đơn #${id}` }).first()
  await step('confirm: thông báo đơn chờ xác nhận mở đúng đơn', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await openNotification(page, `Đơn #${o.id} chờ bạn xác nhận số lượng`)
    await page.waitForURL(/\/orders/)
    await drawer(page).getByText('Lịch sử xử lý').waitFor()
    await drawer(page).getByText(`#${o.id}`).first().waitFor()
    await page.keyboard.press('Escape')
    await cardOf(o.id).waitFor()
  }, page)
  await step('confirm: báo "Số lượng không đúng" cho đơn lẻ', async () => {
    const o = await findOrder(`${TAG} đơn lẻ`)
    await gotoOrder(page, '/orders?view=todo', o.id)
    await cardOf(o.id).getByRole('button', { name: 'Số lượng không đúng' }).click()
    const d = modal(page)
    await d.getByLabel(/Số lượng thực tế/).first().fill('4')
    await d.getByLabel('Lý do sai lệch').fill(`${TAG} thiếu 1 khăn`)
    await d.getByRole('button', { name: 'Gửi báo sai lệch' }).click()
    await toast(page, 'sai lệch')
    expect((await orderStatus(o.id)) === 'DISCREPANCY', 'đơn chưa chuyển sang Có sai lệch')
  }, page)
  await step('confirm: xác nhận số lượng đơn từ yêu cầu lấy đồ', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await gotoOrder(page, '/orders?view=todo', o.id)
    await confirmClick(page, cardOf(o.id).getByRole('button', { name: 'Xác nhận số lượng' }), 'Đúng, xác nhận')
    await toast(page, 'Đã xác nhận đơn')
    expect((await orderStatus(o.id)) === 'WASHING', 'đơn chưa chuyển sang Đang giặt')
  }, page)
  await step('confirm: xác nhận đơn sẽ hủy (để admin hủy khi đang giặt)', async () => {
    const o = await findOrder(`${TAG} sẽ hủy đơn`)
    await gotoOrder(page, '/orders?view=todo', o.id)
    await confirmClick(page, cardOf(o.id).getByRole('button', { name: 'Xác nhận số lượng' }), 'Đúng, xác nhận')
    await toast(page, 'Đã xác nhận đơn')
  }, page)
  await ctx.close()
}

// ============ M5. Admin điều phối đơn ============
if (want('admin-orders')) {
  const { ctx, page } = await open('admin', DESKTOP, 'admin')
  await step('admin: mở lại đơn có sai lệch', async () => {
    const o = await findOrder(`${TAG} đơn lẻ`)
    await gotoOrder(page, '/orders?status=DISCREPANCY', o.id)
    await rowAction(page, o.id, 'Mở lại')
    await page.locator('.ant-popover:visible, .ant-modal-confirm:visible').getByRole('button', { name: 'Đồng ý' }).click()
    await toast(page, `Đơn #${o.id}`)
    expect((await orderStatus(o.id)) === 'PENDING_CONFIRM', 'đơn chưa về Chờ xác nhận')
  }, page)
  await step('admin: đổi hạn trả đơn', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await gotoOrder(page, '/orders?status=WASHING', o.id)
    await rowAction(page, o.id, 'Đổi hạn trả')
    const d = modal(page)
    await d.getByLabel('Hạn trả mới').fill('2030-01-02T10:30')
    await d.getByLabel('Lý do đổi hạn').fill(`${TAG} khách lùi hạn`)
    await d.getByRole('button', { name: 'Lưu' }).click()
    await toast(page, 'Đã đổi hạn trả')
    const due = (await api('admin', `/orders/${o.id}`)).dueAt
    expect(due.startsWith('2030-01-02T10:30'), 'hạn trả không đổi: ' + due)
  }, page)
  await step('admin: bắt đầu giao (đơn đã đủ đơn giá)', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    expect(o.billable, 'đơn chưa đủ đơn giá (bảng giá không tự điền?)')
    await gotoOrder(page, '/orders?status=WASHING', o.id)
    await confirmClick(page, page.locator('tr').filter({ hasText: `#${o.id}` }).getByRole('button', { name: 'Bắt đầu giao' }), 'Đồng ý')
    await toast(page, `Đơn #${o.id}`)
    expect((await orderStatus(o.id)) === 'DELIVERING', 'đơn chưa sang Đang giao')
  }, page)
  await step('admin: hủy đơn kèm lý do', async () => {
    const o = await findOrder(`${TAG} sẽ hủy đơn`)
    await gotoOrder(page, '/orders?status=WASHING', o.id)
    await rowAction(page, o.id, 'Hủy đơn')
    const d = modal(page)
    await d.getByPlaceholder('Lý do hủy (không bắt buộc)').fill(`${TAG} khách đổi ý`)
    await d.getByRole('button', { name: 'Hủy đơn' }).click()
    await toast(page, `Đã hủy đơn #${o.id}`)
    const page2 = await api('admin', `/orders?q=%23${o.id}`)
    expect(page2.total === 0, 'đơn đã hủy vẫn hiện trong danh sách')
  }, page)
  await step('admin: xem chi tiết đơn có nhật ký và in phiếu', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await gotoOrder(page, '/orders?status=DELIVERING', o.id)
    await page.locator('tr').filter({ hasText: `#${o.id}` }).getByRole('button', { name: 'Chi tiết' }).click()
    const dr = drawer(page)
    await dr.getByText('Lịch sử xử lý').waitFor()
    await dr.getByText(`${TAG} khách lùi hạn`).waitFor()
    // Ảnh và chữ ký tài xế đính kèm lúc lấy đồ hiện được (tải kèm token)
    await dr.getByText('Chữ ký khách khi giao đồ bẩn').waitFor()
    const img = dr.locator('.attachment-card img').first()
    await img.waitFor()
    await page.waitForFunction((el) => el.complete && el.naturalWidth > 0, await img.elementHandle())
    const popup = page.waitForEvent('popup', { timeout: 5000 })
    await dr.getByRole('button', { name: /In phiếu/ }).click()
    const w = await popup
    await w.waitForLoadState()
    expect((await w.content()).includes(`${o.id}`), 'phiếu in không có mã đơn')
    await w.close()
  }, page)
  await ctx.close()
}

// ============ M6. Khách nhận đồ / báo thiếu, admin giao bù ============
if (want('receipt')) {
  const { ctx, page } = await open('hoasen.ql', MOBILE, 'quản lý khách')
  await step('receipt: quản lý khách thấy số tiền trên thẻ đơn', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await gotoOrder(page, '/orders?view=todo', o.id)
    const card = page.locator('.order-card').filter({ hasText: `Đơn #${o.id}` }).first()
    await card.getByText('₫').first().waitFor()
  }, page)
  await step('receipt: báo thiếu / hỏng khi nhận đồ', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    const card = page.locator('.order-card').filter({ hasText: `Đơn #${o.id}` }).first()
    await card.getByRole('button', { name: 'Báo thiếu / hỏng' }).click()
    await modal(page).getByLabel('Mô tả đồ bị thiếu hoặc hỏng').fill(`${TAG} thiếu 1 ga`)
    await modal(page).getByRole('button', { name: 'Gửi báo cáo' }).click()
    await toast(page, 'Đã báo sự cố')
    expect((await orderStatus(o.id)) === 'DELIVERY_ISSUE', 'đơn chưa sang Sự cố giao trả')
  }, page)
  await ctx.close()
  const a = await open('admin', DESKTOP, 'admin')
  await step('receipt: admin giao bù', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await gotoOrder(a.page, '/orders?status=DELIVERY_ISSUE', o.id)
    await confirmClick(a.page, a.page.locator('tr').filter({ hasText: `#${o.id}` }).getByRole('button', { name: 'Giao bù' }), 'Đồng ý')
    await toast(a.page, `Đơn #${o.id}`)
  }, a.page)
  await a.ctx.close()
  const c = await open('hoasen.nv', MOBILE, 'nhân viên khách')
  await step('receipt: khách xác nhận đã nhận đủ → hoàn thành', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await gotoOrder(c.page, '/orders?view=todo', o.id)
    await confirmClick(c.page, c.page.locator('.order-card').filter({ hasText: `Đơn #${o.id}` }).getByRole('button', { name: 'Đã nhận đủ đồ' }), 'Đã nhận đủ')
    await toast(c.page, 'Đã xác nhận nhận đồ')
    expect((await orderStatus(o.id)) === 'COMPLETED', 'đơn chưa hoàn thành')
  }, c.page)
  await step('receipt: nhân viên khách không thấy tiền ở trang Đơn giặt', async () => {
    await c.page.goto(APP + '/orders?view=all')
    const text = await c.page.locator('.app-content').textContent()
    expect(!/\d{1,3}(\.\d{3})+\s?₫/.test(text), 'nhân viên khách thấy số tiền')
  }, c.page)
  await c.ctx.close()
}

// ============ M7. Kế toán: nhập giá, thu tiền, hủy thu, sao kê, CSV ============
if (want('finance')) {
  const { ctx, page } = await open('ketoan.mai', DESKTOP, 'kế toán')
  await step('finance: sửa đơn giá đơn đang chờ (Chi tiết & giá)', async () => {
    const o = await findOrder(`${TAG} đơn lẻ`)
    await gotoOrder(page, '/orders?status=PENDING_CONFIRM', o.id)
    await rowAction(page, o.id, 'Chi tiết & giá')
    const d = modal(page)
    await d.getByLabel('Đơn giá dòng 1').fill('7000')
    await d.getByRole('button', { name: 'Lưu' }).click()
    await toast(page, 'Đã')
    const after = await api('admin', `/orders/${o.id}`)
    expect(Number(after.items[0].unitPrice) === 7000, 'đơn giá không đổi: ' + after.items[0].unitPrice)
  }, page)
  let balanceBefore = 0
  await step('finance: ghi nhận thanh toán 1.000 đ cho Hoa Sen', async () => {
    balanceBefore = (await api('admin', '/reports/debts')).find((r) => r.customerName.includes('Hoa Sen')).balance
    await page.goto(APP + '/finance')
    await page.locator('tr').filter({ hasText: 'Hoa Sen' }).getByRole('button', { name: 'Thu tiền' }).click()
    const d = modal(page)
    await d.getByLabel('Số tiền (đ)').fill('1000')
    await d.getByLabel('Ghi chú').fill(`${TAG} chuyển khoản`)
    await d.getByRole('button', { name: 'Ghi nhận' }).click()
    await toast(page, 'Đã')
    const after = (await api('admin', '/reports/debts')).find((r) => r.customerName.includes('Hoa Sen')).balance
    expect(Math.round(balanceBefore - after) === 1000, `công nợ không giảm 1.000: ${balanceBefore} → ${after}`)
  }, page)
  await step('finance: không cho thu vượt công nợ', async () => {
    await page.locator('tr').filter({ hasText: 'Hoa Sen' }).getByRole('button', { name: 'Thu tiền' }).click()
    const d = modal(page)
    await d.getByLabel('Số tiền (đ)').fill(String(Math.round(balanceBefore) + 100000))
    await d.getByRole('button', { name: 'Ghi nhận' }).click()
    await d.getByText('Không được vượt quá công nợ hiện tại').waitFor()
    await d.getByRole('button', { name: 'Hủy' }).click()
  }, page)
  await step('finance: hủy khoản thanh toán vừa ghi (Lịch sử)', async () => {
    await page.locator('tr').filter({ hasText: 'Hoa Sen' }).getByRole('button', { name: 'Lịch sử' }).click()
    const dr = drawer(page)
    await dr.locator('tr').filter({ hasText: `${TAG} chuyển khoản` }).getByRole('button', { name: 'Hủy' }).click()
    await modal(page).getByLabel('Lý do hủy khoản thanh toán').fill('Ghi nhầm')
    await modal(page).getByRole('button', { name: 'Hủy khoản này' }).click()
    // Chờ đúng khoản của lần chạy này được hủy (CSDL có thể còn khoản đã hủy của lần chạy trước; sau khi hủy,
    // cột ghi chú hiện lý do hủy thay cho ghi chú gốc nên không tìm theo dòng trên giao diện được)
    await toast(page, 'Đã hủy')
    for (let i = 0; i < 20; i++) {
      const mine = (await api('admin', '/payments')).find((p) => (p.note ?? '').includes(`${TAG} chuyển khoản`))
      if (mine?.voided) break
      await page.waitForTimeout(250)
    }
    const after = (await api('admin', '/reports/debts')).find((r) => r.customerName.includes('Hoa Sen')).balance
    expect(Math.round(after) === Math.round(balanceBefore), `công nợ không trở lại: ${balanceBefore} → ${after}`)
    await dr.locator('..').locator('.ant-drawer-close').click().catch(() => page.keyboard.press('Escape'))
  }, page)
  await step('finance: xem sao kê tháng có đơn vừa hoàn thành', async () => {
    const o = await findOrder(`${TAG} từ yêu cầu`)
    await page.goto(APP + '/finance')
    await page.locator('tr').filter({ hasText: 'Hoa Sen' }).getByRole('button', { name: 'Sao kê' }).click()
    await modal(page).getByText(`#${o.id}`).first().waitFor()
  }, page)
  // Cần cấu hình ngân hàng nhận tiền (COMPANY_BANK_BIN / COMPANY_BANK_ACCOUNT) trên backend: E2E_QR=1
  if (process.env.E2E_QR) {
    await step('finance: sao kê có mã QR chuyển khoản số còn nợ', async () => {
      const qr = modal(page).locator('.payment-qr')
      await qr.locator('img').waitFor()
      await qr.getByText(/Nội dung: HD-\d{6}-\d{4}/).waitFor()
      const ok = await qr.locator('img').evaluate((el) => el.complete && el.naturalWidth > 100)
      expect(ok, 'ảnh QR không hiển thị')
    }, page)
  }
  await step('finance: tải hóa đơn tháng PDF từ sao kê', async () => {
    const dl = page.waitForEvent('download')
    await modal(page).getByRole('button', { name: 'Hóa đơn PDF' }).click()
    const d = await dl
    expect(d.suggestedFilename().startsWith('hoa-don-') && d.suggestedFilename().endsWith('.pdf'), 'tên file: ' + d.suggestedFilename())
    const head = (await import('node:fs')).readFileSync(await d.path()).subarray(0, 5).toString()
    expect(head === '%PDF-', 'không phải file PDF: ' + head)
  }, page)
  for (const [label, file] of [['Công nợ theo khách (CSV)', 'cong-no'], ['Danh sách đơn (CSV)', 'don-hang'], ['Chi tiết đồ giặt (CSV)', 'chi-tiet']]) {
    await step(`finance: xuất ${label}`, async () => {
      await page.goto(APP + '/finance')
      await page.getByRole('button', { name: 'Xuất báo cáo' }).click()
      const dl = page.waitForEvent('download')
      await page.getByRole('menuitem', { name: label }).click()
      const d = await dl
      expect(d.suggestedFilename().includes(file) && d.suggestedFilename().endsWith('.csv'), 'tên file: ' + d.suggestedFilename())
    }, page)
  }
  await step('finance: nhật ký thay đổi ghi khoản thu và hủy thu vừa làm', async () => {
    await page.goto(APP + '/audit')
    await page.getByText('Thu tiền', { exact: true }).click()
    await page.getByLabel('Tìm trong nhật ký').fill(`${TAG} chuyển khoản`)
    const row = page.locator('tr, .ant-card').filter({ hasText: 'Thu 1.000 đ của Khách sạn Hoa Sen' }).first()
    await row.waitFor()
    await row.getByText('ketoan.mai').first().waitFor()
    await page.getByLabel('Tìm trong nhật ký').fill('Ghi nhầm')
    await page.locator('tr, .ant-card').filter({ hasText: 'Hủy khoản thu 1.000 đ của Khách sạn Hoa Sen' }).first().waitFor()
  }, page)
  await step('finance: báo cáo hiệu suất theo khách và tài xế', async () => {
    await page.goto(APP + '/performance')
    await page.getByText('Giao đúng hạn').first().waitFor()
    await page.locator('tr').filter({ hasText: 'Khách sạn Hoa Sen' }).first().waitFor()
    await page.getByText('Theo tài xế').waitFor()
    await page.getByText('Tháng trước').click()
    await page.getByText('Giao đúng hạn').first().waitFor()
  }, page)
  await ctx.close()

  const m = await open('hoasen.ql', MOBILE, 'quản lý khách')
  await step('finance: quản lý khách xem chất lượng dịch vụ (từ trang Công nợ)', async () => {
    await m.page.goto(APP + '/finance')
    await m.page.getByRole('button', { name: 'Chất lượng dịch vụ' }).click()
    await m.page.waitForURL(/\/performance/)
    await m.page.getByText('Giao đúng hạn').first().waitFor()
    expect((await m.page.getByText('Theo tài xế').count()) === 0, 'quản lý khách thấy số liệu tài xế')
  }, m.page)
  await m.ctx.close()
}

// ============ M8. Admin: khách hàng, tài khoản, bảng giá ============
if (want('admin-manage')) {
  const { ctx, page } = await open('admin', DESKTOP, 'admin')
  const cust = `${TAG} Khách thử`
  const user = `${TAG.toLowerCase().replace(/[^a-z0-9]/g, '')}.tx`
  await step('manage: thêm khách hàng', async () => {
    await page.goto(APP + '/customers')
    await page.getByRole('button', { name: 'Thêm khách hàng' }).first().click()
    const d = modal(page)
    await d.getByLabel('Tên khách hàng').fill(cust)
    await d.getByLabel('Địa chỉ').fill('1 Đường Thử, Q.1')
    await d.getByLabel('Số điện thoại').fill('0909000111')
    await d.getByRole('button', { name: 'Lưu' }).click()
    await toast(page, 'Đã')
    await page.getByLabel('Tìm khách hàng').fill(TAG) // danh sách dài: tìm như người dùng
    await page.locator('tr').filter({ hasText: cust }).waitFor()
  }, page)
  await step('manage: không cho thêm khách trùng tên', async () => {
    await page.getByRole('button', { name: 'Thêm khách hàng' }).first().click()
    const d = modal(page)
    page.__expectError = true
    await d.getByLabel('Tên khách hàng').fill(cust)
    await d.getByLabel('Địa chỉ').fill('x')
    await d.getByRole('button', { name: 'Lưu' }).click()
    await page.locator('.ant-message-notice').filter({ hasText: 'trùng' }).first().waitFor()
    page.__expectError = false
    await d.getByRole('button', { name: 'Hủy' }).click()
  }, page)
  await step('manage: cấp tài khoản cho khách vừa thêm', async () => {
    await page.locator('tr').filter({ hasText: cust }).getByRole('button', { name: 'Cấp tài khoản' }).click()
    const d = modal(page)
    await d.getByLabel('Tên đăng nhập').fill(`${user}.ql`)
    await d.getByLabel('Mật khẩu ban đầu').fill(PW)
    await d.getByRole('button', { name: 'Tạo tài khoản' }).click()
    await toast(page, 'Đã')
  }, page)
  await step('manage: ngừng hợp tác rồi kích hoạt lại', async () => {
    await confirmClick(page, page.locator('tr').filter({ hasText: cust }).getByRole('button', { name: 'Ngừng hợp tác' }), 'Đồng ý')
    await page.locator('tr').filter({ hasText: cust }).getByText('Ngừng hợp tác').first().waitFor()
    await confirmClick(page, page.locator('tr').filter({ hasText: cust }).getByRole('button', { name: 'Kích hoạt' }), 'Đồng ý')
    await page.locator('tr').filter({ hasText: cust }).getByText('Đang hợp tác').waitFor()
  }, page)
  await step('manage: tạo tài khoản tài xế', async () => {
    await page.goto(APP + '/accounts')
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click()
    const d = modal(page)
    await d.getByLabel('Tên đăng nhập').fill(user)
    await d.getByLabel('Họ tên').fill('Tài xế Thử')
    await d.getByLabel('Mật khẩu ban đầu').fill(PW)
    await d.getByRole('button', { name: 'Tạo tài khoản' }).click()
    await toast(page, 'Đã')
    await page.getByLabel('Tìm tài khoản').fill(user)
    await page.locator('tr').filter({ hasText: user }).first().waitFor()
  }, page)
  await step('manage: khóa rồi mở khóa tài khoản', async () => {
    await page.getByLabel('Tìm tài khoản').fill(user)
    const row = page.locator('tr').filter({ hasText: user }).first()
    await confirmClick(page, row.getByRole('button', { name: 'Khóa' }), 'Khóa')
    await row.getByText('Đã khóa').waitFor()
    await row.getByRole('button', { name: 'Mở khóa' }).click()
    await row.getByText('Hoạt động').waitFor()
  }, page)
  await step('manage: đặt lại mật khẩu', async () => {
    await page.locator('tr').filter({ hasText: user }).first().getByRole('button', { name: 'Đặt lại mật khẩu' }).click()
    const d = modal(page)
    await d.getByLabel('Mật khẩu mới').fill(PW)
    await d.getByRole('button', { name: 'Đặt lại' }).click()
    await toast(page, 'Đã đặt lại mật khẩu')
  }, page)
  await step('manage: không tự khóa được chính mình', async () => {
    await page.getByLabel('Tìm tài khoản').fill('admin')
    const disabled = await page.locator('tr').filter({ hasText: /^admin/ }).first().getByRole('button', { name: 'Khóa' }).isDisabled()
    expect(disabled, 'nút Khóa của chính mình vẫn bấm được')
  }, page)
  const item = `${TAG} Rèm cửa`
  await step('manage: thêm mục bảng giá, sửa giá, ẩn', async () => {
    await page.goto(APP + '/prices')
    await page.getByRole('button', { name: 'Thêm vào bảng giá' }).first().click()
    let d = modal(page)
    await d.getByLabel('Tên loại đồ').fill(item)
    await d.getByLabel('Đơn giá chung (đ)').fill('25000')
    await d.getByRole('button', { name: 'Lưu' }).click()
    await toast(page, 'Đã thêm')
    await page.getByLabel('Tìm loại đồ').fill(TAG)
    const row = page.locator('tr').filter({ hasText: item }).first()
    await row.getByRole('button', { name: 'Sửa' }).click()
    d = modal(page)
    await d.getByLabel('Đơn giá chung (đ)').fill('30000')
    await d.getByRole('button', { name: 'Lưu' }).click()
    await toast(page, 'Đã cập nhật')
    await row.getByText('30.000').waitFor()
    await confirmClick(page, row.getByRole('button', { name: 'Ẩn' }), 'Đồng ý')
    await row.getByText('Đã ẩn').waitFor()
  }, page)
  await step('manage: đặt giá riêng cho khách rồi bỏ', async () => {
    await page.getByRole('tab', { name: 'Giá riêng theo khách hàng' }).click()
    await pick(page, page, 'Chọn khách hàng', 'Hoa Sen')
    const row = page.locator('tr').filter({ hasText: 'Khăn' }).filter({ has: page.getByRole('button', { name: 'Đặt giá riêng' }) }).first()
    await row.getByRole('button', { name: 'Đặt giá riêng' }).click()
    await modal(page).getByLabel('Giá riêng cho khách hàng (đ)').fill('1234')
    await modal(page).getByRole('button', { name: 'Lưu' }).click()
    await toast(page, 'Đã đặt giá riêng')
    const r2 = page.locator('tr').filter({ hasText: '1.234' }).first()
    await confirmClick(page, r2.getByRole('button', { name: 'Bỏ' }), 'Đồng ý')
    await toast(page, 'Đã quay về giá chung')
  }, page)
  await ctx.close()

  // Tài khoản mới: đăng nhập (hai thiết bị), đổi mật khẩu, đăng nhập lại
  const t = await open(user, MOBILE, 'tài xế mới')
  const other = await open(user, DESKTOP, 'tài xế mới - máy khác')
  await step('profile: tài khoản mới đăng nhập và đổi mật khẩu', async () => {
    await t.page.goto(APP + '/profile')
    await t.page.getByLabel('Mật khẩu hiện tại').fill(PW)
    await t.page.getByLabel('Mật khẩu mới', { exact: true }).fill('MatKhauMoi9')
    await t.page.getByLabel('Nhập lại mật khẩu mới').fill('MatKhauMoi9')
    await t.page.getByRole('button', { name: 'Đổi mật khẩu' }).click()
    await toast(t.page, 'Đã đổi mật khẩu')
  }, t.page)
  await step('profile: đổi mật khẩu xong, thiết bị đang dùng vẫn đăng nhập', async () => {
    await t.page.goto(APP + '/')
    await t.page.locator('h1').first().waitFor()
    await t.page.waitForLoadState('networkidle')
    if (new URL(t.page.url()).pathname !== '/') throw new Error('bị đăng xuất: ' + t.page.url())
  }, t.page)
  await step('profile: thiết bị khác bị đăng xuất sau khi đổi mật khẩu', async () => {
    other.page.__expectError = true
    await other.page.goto(APP + '/')
    await other.page.waitForURL(/\/login/)
  }, other.page)
  await other.ctx.close()
  await step('profile: đăng nhập lại bằng mật khẩu mới', async () => {
    await t.ctx.clearCookies()
    await t.page.evaluate(() => localStorage.clear())
    await login(t.page, user, 'MatKhauMoi9')
  }, t.page)
  await step('profile: đăng xuất mọi thiết bị', async () => {
    await t.page.goto(APP + '/profile')
    await confirmClick(t.page, t.page.getByRole('button', { name: /Đăng xuất mọi thiết bị/ }), 'Đăng xuất hết')
    await t.page.waitForURL(/\/login/)
    const token = await t.page.evaluate(() => localStorage.getItem('washpro_session'))
    if (token) throw new Error('phiên vẫn còn trong trình duyệt')
  }, t.page)
  await t.ctx.close()
}

// ============ M9. Ứng dụng cài được (PWA) ============
if (want('pwa')) {
  const ctx = await browser.newContext({ viewport: MOBILE, timezoneId: TZ })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => problems.push(`[pwa] JS: ${e.message}`))
  await step('pwa: có manifest, icon cài đặt và service worker', async () => {
    const m = await (await fetch(APP + '/manifest.webmanifest')).json()
    expect(m.display === 'standalone' && m.start_url === '/', 'manifest sai')
    expect(m.icons.some((i) => i.purpose === 'maskable') && m.icons.some((i) => i.sizes === '512x512'), 'thiếu icon 512 / maskable')
    for (const i of m.icons) expect((await fetch(APP + i.src)).ok, 'không tải được icon ' + i.src)
    expect((await fetch(APP + '/sw.js')).ok, 'không tải được sw.js')
  }, page)
  // Service worker chỉ đăng ký ở bản build (CI chạy bằng Docker): E2E_PWA=1
  if (process.env.E2E_PWA) {
    await step('pwa: mất mạng vẫn mở được ứng dụng, có báo mất kết nối', async () => {
      await login(page, 'hoasen.nv')
      await page.evaluate(() => navigator.serviceWorker.ready)
      // Lần tải thứ hai do service worker phục vụ: trang và các gói JS được lưu đệm
      await page.reload()
      await page.locator('h1').first().waitFor()
      await page.waitForLoadState('networkidle')
      await ctx.setOffline(true)
      await page.reload()
      await page.locator('.offline-banner').waitFor()
      await page.locator('.app-header').waitFor()
      // Trang chưa từng mở (gói JS được service worker lưu sẵn lúc cài) cũng mở được khi mất mạng
      await page.goto(APP + '/profile')
      await page.getByText('Thông tin tài khoản').first().waitFor()
      await ctx.setOffline(false)
      await page.locator('.offline-banner').waitFor({ state: 'detached' })
    }, page)
    await step('pwa: trang Tài khoản có công tắc bật thông báo trên điện thoại', async () => {
      await page.goto(APP + '/profile')
      const card = page.locator('.ant-card').filter({ hasText: 'Thông báo trên điện thoại' })
      await card.getByRole('switch', { name: 'Thông báo trên thiết bị này' }).waitFor()
      await card.getByText('Đang tắt trên thiết bị này').waitFor()
    }, page)
  }
  await ctx.close()
}

await browser.close()
console.log('\n===== KẾT QUẢ =====')
console.log(`${results.filter((r) => r[0] === 'PASS').length} PASS / ${results.filter((r) => r[0] === 'FAIL').length} FAIL`)
for (const r of results.filter((r) => r[0] === 'FAIL')) console.log('FAIL', r[1], '-', r[2])
console.log(problems.length ? '\n===== LỖI API / JS / CẢNH BÁO =====\n' + [...new Set(problems)].join('\n') : '\nKhông có lỗi API / JS')
console.log('TAG', TAG)
const failed = results.some((r) => r[0] === 'FAIL') || problems.length > 0
process.exitCode = failed ? 1 : 0
