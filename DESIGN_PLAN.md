# Phương án thiết kế giao diện – THANHXUAN

Giai đoạn 2: phân tích và lập kế hoạch. **Chưa sửa code giao diện.** Tài liệu này là nguồn duy nhất cho việc chỉnh sửa
giao diện ở các giai đoạn sau; mọi thay đổi giao diện cần đối chiếu với mục 7 (ưu tiên) và mục 8 (chức năng không được làm hỏng).

Ràng buộc chung: không viết lại dự án, không đổi business logic, API, CSDL, xác thực; không xóa chức năng; không thêm
thư viện lớn; không tạo chức năng mới mà hệ thống chưa hỗ trợ.

## Trạng thái triển khai (10/10/2026)

Đã triển khai bước 1–5 của mục 9. Kiểm tra bằng ảnh chụp Edge headless ở 375px và 1440px với tài khoản quản lý xưởng,
tài xế, kế toán, quản lý phía khách (gồm các modal: tạo đơn, chi tiết & giá, thêm khách hàng, tạo tài khoản, thêm mục giá,
ghi nhận thanh toán, yêu cầu lấy đồ); mọi trang có đúng một `h1`, không có thanh cuộn ngang, không có lỗi JavaScript.

| Mã | Trạng thái | Ghi chú |
|---|---|---|
| A1 S1 S5 | Xong | `STATUS_TONES` (`theme.ts`), `utils/statusLook.tsx`, `StatusTag`, `PickupStatusTag`, `CategoryTag`, `ToneTag` |
| A2 A4 A5 A6 T4 | Xong | `index.css`: nhóm menu 60%, viền focus, `prefers-reduced-motion`, link bỏ qua, focus khi đổi trang (`MainLayout`) |
| T1 T2 | Xong | `TouchFriendly` bọc toàn bộ nội dung trong `MainLayout`: < 768px nút 44px (nút nhỏ 36px), ô nhập 16px |
| T3 | Xong | `CatalogLines` tự xuống hàng; modal form `sheet-modal` toàn màn hình trên điện thoại |
| C1 F1 | Xong | `/pickups?new=1`; `EmptyState` có hành động (Lấy đồ, Đơn hàng, Khách hàng, Bảng giá) |
| L1 L2 L3 | Xong | `ResponsiveTable` (bảng → thẻ trên điện thoại) cho Công nợ, Khách hàng, Bảng giá, Tài khoản; `PageHeader` mọi trang |
| G1 G2 A3 A7 | Xong | Sider và Tổng quan phẳng; `StatStrip` thay 14 thẻ; bỏ emoji; đăng nhập còn một lớp gradient nhẹ |
| S2 S3 | Xong | Màu khai báo một lần trong `theme.ts`, ghi ra biến CSS bằng `applyCssVariables()`; không còn hex trong component |
| S4 | Phần lớn | Class `.mt-2 .mt-4 .mb-3 .mb-4` theo thang `--space-*`; `LoadError` thay hộp lỗi chép ở 7 trang; inline style 107 → 76, còn lại là độ rộng ô lọc và giá trị động |

Ngoài kế hoạch: màu lỗi / cảnh báo mặc định của antd (~3:1) và `colorTextDescription` (2,66:1) được đổi trong theme để đạt tương phản;
nền / viền hộp thông báo đặt theo `STATUS_TONES` (antd tự sinh từ màu đậm ra màu nâu xám); ô phân công tài xế có nhãn;
lời chào gọi đủ họ tên khi họ tên là tên vai trò (VD "Quản lý xưởng").

## Trải nghiệm theo vai trò

Mỗi vai trò có trang chủ, menu và cách nhóm đơn riêng theo công việc thật của họ. Chỉ dùng API sẵn có, không đổi phân quyền.
Thao tác trên đơn và trên yêu cầu lấy đồ dùng chung một nguồn (`pages/orders/useOrderActions.tsx`,
`pages/pickups/usePickupActions.tsx`), nên làm được ngay trên trang chủ, không phải chuyển trang.

| Vai trò | Thiết bị | Trang chủ (`pages/home/`) | Menu | Trang Đơn hàng |
|---|---|---|---|---|
| Tài xế | Điện thoại | **Việc hôm nay**: số việc cần đi hôm nay / đang chờ tài xế; "Việc của tôi" với nút **Gọi**, **Chỉ đường**, **Đã đến · Tạo đơn**; việc đang chờ (nhận ngay); đơn chờ khách xác nhận (báo sai lệch) | Hôm nay · Lấy đồ · Đơn đã tạo · Tài khoản | "Đơn đã tạo": Tất cả / Chờ khách xác nhận / Đang giặt-giao / Hoàn thành |
| Nhân viên phía khách | Điện thoại | **Trang chủ**: nút **Yêu cầu lấy đồ** mở form tại chỗ; "Cần bạn xác nhận" với nút **Xác nhận số lượng** / **Đã nhận đủ đồ** / báo sai; yêu cầu đang chờ ("Tài xế … sẽ đến"); đồ đang ở xưởng | Trang chủ · Lấy đồ · Đơn giặt · Tài khoản | "Đơn giặt": Cần bạn xác nhận / Đang ở xưởng / Hoàn thành / Tất cả; không có cột khách hàng, không có tiền |
| Quản lý phía khách | Điện thoại | Như nhân viên + **Công nợ với xưởng** (còn nợ, quá hạn, đã trả) và nút **Sao kê tháng** | + Công nợ | Như nhân viên, có số tiền |
| Quản lý xưởng | Máy tính | **Điều phối**: chip việc tồn đọng (trễ hạn, chưa có tài xế, lấy đồ quá hẹn, sai lệch, sự cố); đơn cần xử lý dạng dòng có nút làm ngay; phân công tài xế tại chỗ; trạng thái; tài chính | Điều phối · Lấy đồ · Đơn hàng · Khách hàng · Bảng giá · Tài khoản · Công nợ | Tab đủ 6 trạng thái; Lấy đồ: Chưa có tài xế / Đã có tài xế / Đã xong |
| Kế toán | Máy tính | **Đơn cần nhập đơn giá** (đang giặt lên đầu) với nút **Nhập đơn giá**; tài chính; trạng thái | Tổng quan · Lấy đồ · Đơn hàng · Bảng giá · Công nợ | Tab đủ 6 trạng thái |

Quy tắc chung: thẻ đơn có dòng nhắc "việc cần làm tiếp" theo vai trò (`pages/orders/hints.ts`); nhãn nút nói rõ hành
động ("Xác nhận số lượng", "Đã nhận đủ đồ", "Số lượng không đúng", "Nhập đơn giá"); xưởng xem trên điện thoại thì thẻ
thu gọn còn "Chi tiết" + thao tác chính + "Thêm"; danh sách lấy đồ mở sẵn tab đầu tiên đang có việc.

Đã kiểm tra bằng ảnh cả 5 vai trò. Riêng nhân viên phía khách (`hoasen.nv`) có thêm phép đo: không trang nào hiện số tiền,
menu không có Công nợ, mở thẳng `/finance` bị chuyển về trang chủ (đối chứng: tài khoản quản lý phía khách thấy số tiền).
Lời chào chỉ gọi tên khi họ tên là tên người; họ tên là chức danh ("Nhân viên Khách sạn Hoa Sen", "Quản lý xưởng") thì
chỉ chào "Chào buổi sáng". Khối trống trên trang chủ là một dòng gọn để phần có việc nằm ngay màn hình đầu.

## Mục lục

1. [Hiện trạng kỹ thuật](#1-hiện-trạng-kỹ-thuật)
2. [Kết quả audit (giai đoạn 1)](#2-kết-quả-audit-giai-đoạn-1)
3. [Người dùng và mục tiêu chuyển đổi](#3-người-dùng-và-mục-tiêu-chuyển-đổi)
4. [Hướng thiết kế](#4-hướng-thiết-kế)
5. [Design system](#5-design-system)
6. [Cấu trúc từng trang](#6-cấu-trúc-từng-trang)
7. [Mức độ ưu tiên P0 / P1 / P2](#7-mức-độ-ưu-tiên-p0--p1--p2)
8. [Chức năng không được làm hỏng](#8-chức-năng-không-được-làm-hỏng)
9. [Thứ tự triển khai](#9-thứ-tự-triển-khai)
10. [Ngoài phạm vi](#10-ngoài-phạm-vi)

---

## 1. Hiện trạng kỹ thuật

| Hạng mục | Hiện trạng (giữ nguyên) |
|---|---|
| Framework | React 19 + TypeScript, Vite 8 |
| Thư viện UI | Ant Design 6, icon `@ant-design/icons` |
| Styling | Token antd trong `src/theme.ts` + CSS toàn cục `src/index.css` (biến `:root`) |
| Routing | React Router 7; trang tải lười (`lazy`); `ProtectedRoute` + `RoleRoute` chặn theo vai trò |
| Font | Be Vietnam Pro (Google Fonts, hỗ trợ đầy đủ dấu tiếng Việt) |
| Responsive | `Grid.useBreakpoint()` của antd; máy tính khi ≥ 992px (`lg`) |

**Trang:** Đăng nhập, Tổng quan, Lấy đồ, Đơn hàng, Khách hàng, Bảng giá, Tài khoản, Công nợ & thanh toán, Thông tin tài khoản.

**Modal / drawer:** Tạo đơn, Báo sai lệch, Chi tiết & giá, Chi tiết đơn (drawer, in phiếu giao nhận), Sao kê tháng,
Lịch sử thanh toán, Ghi nhận / hủy thanh toán, các form tạo/sửa khách hàng, bảng giá, tài khoản.

**Component dùng chung:** `MainLayout` (sider, header, drawer, thanh điều hướng dưới), `KpiCard`, `StatusTag`, `DueTag`,
`CatalogLines` (nhập dòng đồ giặt). Tiện ích: `utils/format.ts`, `utils/catalog.ts`, `utils/print.ts`.

## 2. Kết quả audit (giai đoạn 1)

Audit giai đoạn 1 chỉ được trình bày trong hội thoại, chưa có file riêng; bảng dưới đây là bản lưu chính thức.
Mã lỗi (A1, T1…) được dùng lại ở mục 7 và 9. Độ tương phản đã được tính theo công thức WCAG.

**Điểm tốt cần giữ:** màu primary đạt 4,56:1 với chữ trắng; chữ phụ đạt ~5:1; thanh điều hướng dưới cho vai trò dùng
điện thoại; trang Đơn hàng có dạng bảng (máy tính) và dạng thẻ (điện thoại), một thao tác chính mỗi đơn, thao tác nguy hiểm
đều hỏi lại; có trạng thái tải, lỗi (kèm "Thử lại"), rỗng; hạn trả hiện bằng chữ + icon; biểu đồ có `aria-label`.

| Mã | Nhóm | Vấn đề | Bằng chứng |
|---|---|---|---|
| A1 | Truy cập | Nhãn trạng thái không đủ tương phản | "Chờ xác nhận" (gold) 2,76:1; "Đang giao" (cyan) 3,39:1; cần ≥ 4,5:1 |
| A2 | Truy cập | Tiêu đề nhóm menu trái 11px, 3,62:1 | `.ant-menu-item-group-title` |
| A3 | Truy cập | Viền nút phụ trên băng chào 2,5:1 (cần ≥ 3:1) | `.hero .ant-btn-default` |
| A4 | Truy cập | Phần tử tự làm thiếu viền focus bàn phím | `.pipeline-step`, `.bottom-nav button` |
| A5 | Truy cập | Không tôn trọng `prefers-reduced-motion` | `index.css` |
| A6 | Truy cập | Trang bắt đầu bằng `h3`, không có `h1`; Tổng quan dùng `h5`; không có link "bỏ qua tới nội dung" | `Typography.Title level={3}` |
| A7 | Phong cách | Emoji trong giao diện | 👋 lời chào, 🎉 trạng thái rỗng (Tổng quan) |
| T1 | Điện thoại | Ô nhập 14px làm iOS Safari tự phóng to khi chạm | `fontSize: 14` toàn cục |
| T2 | Điện thoại | Vùng bấm nhỏ: `controlHeight` 36px, nút trên thẻ 32px, nút trong bảng 24px | `theme.ts`, `size="small"` |
| T3 | Điện thoại | Form tạo đơn vỡ bố cục: Select cố định 290px, modal 720–860px | `CatalogLines.tsx`, các modal đơn |
| T4 | Điện thoại | Nhãn thanh điều hướng dưới 11px | `.bottom-nav button` |
| L1 | Bố cục | Bảng Công nợ rộng 1000px trên điện thoại (quản lý phía khách dùng điện thoại) | `Finance.tsx` `scroll.x: 1000` |
| L2 | Bố cục | Khách hàng, Bảng giá, Tài khoản chỉ có bảng (kéo ngang trên điện thoại) | |
| L3 | Bố cục | Đầu trang không thống nhất (`page-toolbar` ở 6 trang, Tổng quan / Hồ sơ khác) | |
| S1 | Nhất quán | Một trạng thái có 2 bộ màu: preset antd (`STATUS_META`) và hex riêng (`STATUS_DOT`) | `types.ts`, `Dashboard.tsx` |
| S2 | Nhất quán | Hex viết cứng trong component (12 ở `KpiCard`, 8 ở Tổng quan, rải rác nơi khác) | |
| S3 | Nhất quán | Màu khai báo 2 lần: `BRAND` (`theme.ts`) và `:root` (`index.css`) | |
| S4 | Nhất quán | ~120 inline style cho khoảng cách / cỡ chữ không theo thang chung | |
| S5 | Nhất quán | Màu trạng thái lấy đồ chưa kiểm tra tương phản | `PICKUP_STATUS_META` |
| F1 | Phản hồi | Trạng thái rỗng chỉ có chữ, không có hành động tiếp theo | Lấy đồ, Đơn hàng, Tổng quan |
| G1 | Phong cách | Lạm dụng gradient: sider, băng chào Tổng quan (radial + linear), trang đăng nhập (3 lớp) | `index.css` |
| G2 | Phong cách | Quá nhiều thẻ: Tổng quan có tới 14 thẻ (4 KPI + 6 ô trạng thái + 4 KPI tài chính) | `Dashboard.tsx` |
| C1 | Chuyển đổi | Nút "Yêu cầu lấy đồ" ở Tổng quan chỉ chuyển trang, khách phải bấm thêm lần nữa mới mở form | `HERO` → `/pickups` |

## 3. Người dùng và mục tiêu chuyển đổi

Đây là **ứng dụng vận hành B2B có đăng nhập**, không phải website công khai cho người tiêu dùng. Không có trang chủ
quảng cáo, không có đăng ký tài khoản, khách không tự chọn dịch vụ hay xem bảng giá (bảng giá chỉ dành cho quản lý / kế toán;
tài xế chọn loại đồ khi đếm). Vì vậy "đặt lịch giặt / nhận đồ tận nơi" trong hệ thống này chính là **Yêu cầu lấy đồ**.

| Nhóm | Vai trò | Thiết bị, bối cảnh | Việc chính |
|---|---|---|---|
| Khách hàng doanh nghiệp | Nhân viên / Quản lý phía khách (nhà hàng, khách sạn, spa…) | Điện thoại, bận rộn, thao tác giữa ca | Gửi yêu cầu lấy đồ; xác nhận số lượng; xác nhận đã nhận đủ hoặc báo thiếu/hỏng; (quản lý) xem công nợ, sao kê |
| Tài xế | Tài xế | Điện thoại, ngoài trời, một tay, mạng chập chờn | Nhận việc; gọi / chỉ đường; đếm đồ và tạo đơn tại chỗ; báo sai lệch |
| Xưởng | Quản lý xưởng | Máy tính | Điều phối, phân công, chuyển trạng thái, đổi hạn, quản lý khách / tài khoản / bảng giá |
| Xưởng | Kế toán | Máy tính | Nhập đơn giá, ghi nhận thanh toán, công nợ, sao kê, xuất CSV |

**Mục tiêu chuyển đổi (theo thứ tự):**

1. **Khách gửi Yêu cầu lấy đồ** nhanh nhất có thể: từ Tổng quan đến khi gửi xong ≤ 3 thao tác (hiện tại 4–5).
2. **Khách xác nhận số lượng / đã nhận đủ** đúng lúc: đây là điểm chốt để đơn chạy tiếp và vào công nợ.
3. **Tài xế tạo đơn tại chỗ** không lỗi nhập liệu trên điện thoại.
4. **Xưởng thấy ngay việc trễ hạn / cần xử lý** khi mở Tổng quan.

**Vấn đề UX ưu tiên** (chi tiết ở mục 7): tương phản trạng thái (A1), form tạo đơn trên điện thoại (T1–T3), vùng bấm (T2),
CTA lấy đồ bị gián đoạn (C1), Công nợ trên điện thoại (L1).

## 4. Hướng thiết kế

**"Vận hành sạch, rõ ràng, đáng tin"** – giao diện phẳng, nền sáng, nhấn bằng một màu thương hiệu, thông tin xếp theo
mức độ khẩn cấp. Cảm giác "sạch" đến từ khoảng trắng, chữ dễ đọc và trạng thái rõ ràng, không đến từ hiệu ứng.

Nguyên tắc:

1. **Trạng thái là trung tâm.** Mỗi đơn / yêu cầu luôn hiện nhãn trạng thái (chữ + màu + icon) và hạn trả. Màu trạng thái
   có một nguồn duy nhất.
2. **Một hành động chính mỗi khu vực.** Nút primary dành cho bước tiếp theo của luồng (Gửi yêu cầu, Nhận việc, Xác nhận,
   Đã nhận đủ). Các nút khác ở mức phụ.
3. **Điện thoại trước cho khách và tài xế**, máy tính trước cho xưởng. Cùng một component, hai cách trình bày.
4. **Ít thẻ, ít gradient.** Thẻ chỉ dùng cho một đối tượng độc lập (một đơn, một yêu cầu lấy đồ) hoặc một khối cần viền
   bao. Gradient chỉ còn ở panel thương hiệu trang đăng nhập (một lớp, nhẹ). Sider và băng chào chuyển sang màu phẳng.
5. **Giữ nhận diện hiện có:** xanh ngọc + xanh đậm lấy từ logo (gợi "nước, sạch"), font Be Vietnam Pro, icon antd.
   Không đổi sang bảng màu / font chung chung do công cụ gợi ý (Calistoga không hỗ trợ đủ tiếng Việt).

Tránh: gradient trang trí, glassmorphism, bóng đổ đậm, thẻ lồng thẻ, emoji, animation không mang ý nghĩa, màu là kênh
thông tin duy nhất.

## 5. Design system

Toàn bộ giá trị khai báo **một lần trong `src/theme.ts`**: từ đó sinh token antd và biến CSS (`:root`) – xử lý S2, S3.
Component không dùng hex trực tiếp.

### 5.1 Màu

**Màu thương hiệu và giao diện**

| Token | Giá trị | Dùng cho | Ghi chú |
|---|---|---|---|
| `primary` | `#0E8385` | Nút chính, liên kết trên nền trắng, tab đang chọn, viền focus | Chữ trắng 4,56:1 (giữ nguyên) |
| `primary-hover` | `#0B6E70` | Hover / active nút chính; **liên kết trên nền xám** | Chữ trắng 6,04:1; `primary` trên nền `bg` chỉ 4,2:1 nên liên kết trên nền xám dùng màu này |
| `primary-soft` | `#E6F4F4` | Nền mục được chọn, ô icon KPI | |
| `navy` | `#124D6B` | Màu phụ: tiêu đề nhấn, biểu tượng thương hiệu | |
| `navy-deep` | `#0C3247` | Nền sider / drawer menu (phẳng, bỏ gradient) | |
| `teal` | `#14A0A1` | Chỉ trang trí / biểu đồ, **không dùng cho chữ** | |
| `bg` | `#F3F6F8` | Nền trang | |
| `surface` | `#FFFFFF` | Thẻ, bảng, modal, header | |
| `surface-muted` | `#F6F9FA` | Đầu bảng, vùng phụ | |
| `text` | `#1B2B36` | Chữ chính | |
| `text-muted` | `#5B6B76` | Chữ phụ, nhãn | 5,08:1 trên `bg`, 5,51:1 trên trắng |
| `text-on-dark` | `#FFFFFF` / trắng 72% | Chữ trên sider | 72% đạt 7,71:1 |
| `text-on-dark-subtle` | trắng 60% | Tiêu đề nhóm menu (thay 42%) – sửa A2 | 5,82:1 |
| `border` | `#E3E9ED` | Viền thẻ, đường kẻ | |
| `success` / `warning` / `danger` / `info` | `#1E7234` / `#8A5A00` / `#B42318` / `#1F4FB8` | Chữ thông báo, giá trị nhấn mạnh (thay `#C2341B` rải rác) | Đều ≥ 5:1 trên trắng |

**Bảng màu trạng thái (nguồn duy nhất – sửa A1, S1, S5).** Mỗi trạng thái gồm chữ, nền, icon; chấm màu và viền nhấn dùng
màu chữ. Đã kiểm tra: chữ/nền ≥ 5,2:1; chấm trên trắng ≥ 5,9:1.

| Trạng thái đơn | Chữ | Nền | Icon (antd) | Tỷ lệ chữ/nền |
|---|---|---|---|---|
| Chờ xác nhận | `#8A5A00` | `#FFF4D6` | `HourglassOutlined` | 5,41 |
| Có sai lệch | `#B42318` | `#FEEBE9` | `ExclamationCircleOutlined` | 5,72 |
| Đang giặt | `#1F4FB8` | `#E8EFFD` | `SyncOutlined` (không quay) | 6,34 |
| Đang giao | `#0B6466` | `#E0F2F2` | `CarOutlined` | 5,99 |
| Sự cố giao trả | `#A2400B` | `#FDEADB` | `WarningOutlined` | 5,49 |
| Hoàn thành | `#1E7234` | `#E6F4E9` | `CheckCircleOutlined` | 5,26 |

Trạng thái lấy đồ dùng lại các cặp trên: Chờ tài xế nhận = Chờ xác nhận; Tài xế đã nhận = Đang giặt (xanh dương);
Đã lấy đồ = Hoàn thành; Đã hủy = `#4A5761` trên `#EEF1F3` (6,55:1).

**Biểu đồ tuổi nợ:** giữ `notDue #0A9FA3` (3,23:1), `d60 #D0602A`, `over60 #8F2410`; đổi `d30` từ `#E8A33A` (2,16:1)
sang `#B57E0F` (3,53:1). Các đoạn cách nhau 2px nền trắng và chú thích luôn kèm số tiền, nên không phụ thuộc màu.

Nhóm đồ (`CATEGORY_META`) giữ preset antd vì chỉ là phân loại phụ, luôn kèm chữ; kiểm tra lại tương phản ở giai đoạn 1.

### 5.2 Typography

Font: **Be Vietnam Pro** 400 / 500 / 600 / 700, `font-display: swap` (đã có). Số liệu, tiền, ngày giờ: `tabular-nums`.

| Cấp | Thẻ | Máy tính | Điện thoại | Độ đậm | Dùng cho |
|---|---|---|---|---|---|
| Tiêu đề trang | `h1` | 24 / 32 | 20 / 28 | 700 | Một lần mỗi trang, trong `PageHeader` (sửa A6) |
| Tiêu đề khối | `h2` | 18 / 26 | 16 / 24 | 600 | "Việc cần bạn xử lý", "Công nợ theo khách" |
| Tiêu đề thẻ | `h3` / title thẻ | 16 / 24 | 15 / 22 | 600 | Tiêu đề thẻ, modal |
| Nội dung | – | 14 / 22 | 14 / 22 | 400 | Văn bản, bảng |
| **Ô nhập** | – | 14 | **16** | 400 | Chống iOS tự phóng to (sửa T1) |
| Nhãn / phụ | – | 13 / 20 | 13 / 20 | 400–500 | Nhãn KPI, mô tả phụ |
| Nhỏ nhất | – | 12 / 18 | 12 / 18 | 400–600 | Chỉ cho metadata, nhãn thanh điều hướng dưới (sửa T4: 11 → 12) |
| Số KPI | – | 24 | 20 | 700 | Giá trị chỉ số |

Không dùng chữ < 12px. Không viết hoa toàn bộ trừ tiêu đề nhóm menu (giữ letter-spacing hiện có).

### 5.3 Spacing, layout, grid

- **Thang khoảng cách (bước 4):** `4 · 8 · 12 · 16 · 20 · 24 · 32 · 48` → biến `--space-1` … `--space-8`. Thay dần inline
  style (S4) bằng biến / prop `gap` của antd.
- Khoảng cách chuẩn: trong thẻ 16 (điện thoại 14); giữa các phần tử cùng nhóm 8–12; giữa các khối 24; đầu trang → nội dung 20.
- **Khung nội dung:** rộng tối đa 1440px, căn giữa. Lề: 24px (máy tính), 16px (máy tính bảng), 12–16px (điện thoại).
- **Grid:** `Row`/`Col` 24 cột của antd. Gutter 16 (máy tính), 12 (điện thoại).
- **Form một cột** rộng tối đa 560px (Hồ sơ, form trong modal); hai cột chỉ cho cặp trường ngắn liên quan (Ngày lấy – Khung giờ).
- **z-index:** header 10, thanh điều hướng dưới 30, còn lại theo antd (modal, drawer, message).

### 5.4 Bo góc và đổ bóng

| Token | Giá trị | Dùng cho |
|---|---|---|
| `radius-sm` | 6 | Tag, ô nhỏ |
| `radius-md` | 10 | Nút, ô nhập, mục menu (giữ `borderRadius` antd hiện tại) |
| `radius-lg` | 14 | Thẻ, modal, drawer (giữ `borderRadiusLG`) |
| `shadow-1` | `0 1px 2px rgba(12,50,71,.04), 0 4px 16px rgba(12,50,71,.05)` | Thẻ (hiện có) |
| `shadow-2` | do antd quản lý | Lớp nổi: modal, dropdown, drawer |

Không thêm mức bóng nào khác; không dùng `transform` nhấc thẻ khi hover (thay bằng đổi màu viền).

### 5.5 Quy tắc component

**Button**
- Tối đa **một nút primary** mỗi khu vực (đầu trang, thẻ, modal). Thứ tự: primary → default → text/link.
- `danger` chỉ cho thao tác phá hủy và luôn có xác nhận (giữ cơ chế Popconfirm / Modal hiện có).
- Nút chỉ có icon: bắt buộc `aria-label` + `Tooltip`.
- Trạng thái tải: dùng `loading` của antd trên đúng nút được bấm (đã có, giữ).
- **Chiều cao:** 36px trên máy tính; **44px trên điện thoại** (<768px) cho mọi nút trong thẻ, form, modal (sửa T2).
  Nút trong bảng máy tính giữ `small` (24px, đạt WCAG 2.2 AA).
- Trên điện thoại, nút hành động trong thẻ và chân modal **giãn hết chiều ngang** (`block`).

**Form**
- Nhãn luôn hiện phía trên ô; placeholder chỉ là ví dụ, không thay nhãn.
- Đánh dấu bắt buộc thống nhất (dấu `*` mặc định của antd); trường không bắt buộc ghi "(không bắt buộc)" như hiện tại.
- Lỗi hiện ngay dưới ô (cơ chế antd); thông báo nêu cách sửa (giữ nội dung lỗi hiện tại từ backend).
- `inputMode` đúng loại (`numeric` cho số túi, số lượng; `tel` cho điện thoại); `autoComplete` cho đăng nhập (đã có).
- Lựa chọn ít phương án (Khung giờ: 4 lựa chọn) dùng `Segmented` / `Radio.Group` dạng nút thay vì `Select` – bớt một lần chạm.
- **Modal form trên điện thoại hiển thị toàn màn hình**, chân modal dính đáy chứa nút chính (sửa T3).

**Navigation**
- Máy tính (≥ 992px): sider 248px, menu theo nhóm (giữ). Sider màu phẳng `navy-deep` (bỏ gradient – G1).
- Máy tính bảng / điện thoại – vai trò xưởng: header + nút mở drawer menu (giữ).
- Điện thoại – tài xế, khách: thanh điều hướng dưới ≤ 5 mục, có icon + chữ, mục hiện tại có vạch và `aria-current` (giữ).
- Thêm link "Bỏ qua tới nội dung" (ẩn, hiện khi focus) và đưa focus về vùng nội dung khi đổi trang (A6).
- Trạng thái lọc nằm trên URL (đã có `?status=`, `?overdue=1`) – giữ để quay lại / chia sẻ link được.

**Card**
- Dùng cho: một đối tượng có thao tác (đơn trên điện thoại, yêu cầu lấy đồ), một khối nội dung cần viền (bảng, biểu đồ).
- Không dùng thẻ cho: từng ô số liệu nhỏ đứng cạnh nhau (gộp thành một thẻ chia cột), tiêu đề khối, thẻ lồng thẻ (G2).
- Thẻ có trạng thái cần chú ý (trễ hạn, quá ngày hẹn): viền trái 3px màu trạng thái + nhãn chữ, không tô cả viền đỏ.

**Icon**
- Chỉ dùng `@ant-design/icons`, kiểu **Outlined** cho giao diện; Filled chỉ để nhấn trạng thái (ví dụ dấu tích ở đăng nhập).
- Kích thước: 16 (trong chữ / nút), 20 (thanh điều hướng, KPI điện thoại), 24 (thanh điều hướng dưới, KPI máy tính).
- Icon cạnh chữ là trang trí → ẩn khỏi trình đọc màn hình; icon đứng một mình phải có nhãn.
- Không dùng emoji (A7).

**Phản hồi**
- Rỗng: hình đơn giản + câu giải thích + **nút hành động** nếu có bước tiếp theo hợp lệ cho vai trò (F1).
  Ví dụ: khách chưa có yêu cầu → nút "Yêu cầu lấy đồ"; tài xế chưa nhận việc → nút "Xem việc chờ nhận".
- Lỗi tải: `Alert` + "Thử lại" (giữ). Thành công: `message` 3s (giữ).
- Chuyển động: 150ms (hover, đổi màu), 250ms (mở / đóng lớp). Tắt khi `prefers-reduced-motion: reduce` (A5).
- Focus: viền 2px `primary`, cách 2px, cho mọi phần tử bấm được, kể cả phần tử tự làm (A4).

### 5.6 Responsive

| Khoảng | Bố cục | Điều hướng | Danh sách | Modal form |
|---|---|---|---|---|
| **Điện thoại** < 768 | Một cột; lề 12–16; nút 44px, ô nhập 16px | Thanh dưới (tài xế, khách) / drawer (xưởng) | Thẻ | Toàn màn hình, chân dính đáy |
| **Máy tính bảng** 768–991 | Hai cột cho thẻ; lề 16 | Drawer | Thẻ (trang khách / tài xế), bảng (trang quản trị) | Modal giữa màn hình, rộng tối đa 90% |
| **Máy tính** ≥ 992 | Sider + nội dung ≤ 1440 | Sider | Bảng | Modal theo độ rộng hiện tại |

Kiểm tra ở 375, 768, 1024, 1440px; không được có thanh cuộn ngang ở cấp trang trên điện thoại (bảng được phép cuộn trong
khung riêng chỉ ở máy tính bảng / máy tính).

## 6. Cấu trúc từng trang

Ký hiệu: **[CTA]** nút chính của trang. Mọi trang dùng `PageHeader` (h1 + mô tả ngắn + nút hành động) – sửa L3.
Thứ tự section là thứ tự từ trên xuống. Chỉ sắp xếp lại và trình bày lại chức năng sẵn có.

### Đăng nhập
1. Panel thương hiệu (logo, khẩu hiệu, 3 điểm lợi ích; gradient một lớp nhẹ). Điện thoại: thu gọn thành dải logo + khẩu hiệu.
2. Form: Tên đăng nhập, Mật khẩu, **[CTA] Đăng nhập**, cảnh báo phiên hết hạn / lỗi, hướng dẫn quên mật khẩu.

### Tổng quan (theo vai trò)
Bỏ băng chào gradient (G1); lời chào + ngày đưa vào `PageHeader`. Gộp thẻ (G2).

| Vai trò | Thứ tự section |
|---|---|
| Nhân viên / Quản lý phía khách | 1. `PageHeader` + **[CTA] Yêu cầu lấy đồ** (mở thẳng form – sửa C1) · 2. "Cần bạn xác nhận" (đơn chờ xác nhận / sai lệch / đang giao – có nút thao tác ngay) · 3. Dải trạng thái đơn · 4. (Quản lý) Tóm tắt công nợ: một thẻ, 4 dòng số liệu, link "Xem công nợ" |
| Tài xế | 1. `PageHeader` + **[CTA] Việc lấy đồ** · 2. Việc cần làm (đơn đang giao / sai lệch, trễ hạn lên đầu) · 3. Dải trạng thái đơn |
| Quản lý xưởng | 1. `PageHeader` + **[CTA] Phân công lấy đồ** · 2. Cảnh báo (đơn trễ hạn, hạn trả hôm nay – chỉ hiện khi > 0) · 3. Chỉ số trong ngày (một thẻ chia 4 cột) · 4. Dải trạng thái đơn · 5. Việc cần xử lý · 6. Tóm tắt tài chính |
| Kế toán | 1. `PageHeader` + **[CTA] Công nợ** · 2. Đơn đang giặt cần chốt giá · 3. Tóm tắt tài chính · 4. Dải trạng thái đơn |

Dải trạng thái đơn: một thẻ duy nhất chia 6 ô (thay 6 thẻ), mỗi ô là link có focus, chấm màu lấy từ bảng màu trạng thái.

### Lấy đồ
1. `PageHeader`: mô tả theo vai trò + **[CTA] Yêu cầu lấy đồ** (khách) / **Tạo yêu cầu hộ khách** (quản lý xưởng); nút Làm mới phụ.
2. Tab (giữ): tài xế "Việc của tôi / Chờ nhận / Đã xong"; khác "Đang chờ / Đã xong".
3. Danh sách thẻ, sắp theo ngày hẹn. Thẻ: trạng thái + ngày/khung giờ (nổi bật "Hôm nay", "Quá ngày hẹn") → địa chỉ (mở bản đồ),
   điện thoại (gọi) cho tài xế / xưởng → số túi, ghi chú → nút thao tác full-width (Nhận việc / Đã đến · Tạo đơn / Trả lại /
   Phân công / Hủy).
4. Rỗng có hành động (F1).
5. Form yêu cầu: (Khách hàng – chỉ xưởng) → Ngày lấy → Khung giờ (nút chọn nhanh) → Số túi ước tính → Ghi chú → **[CTA] Gửi yêu cầu**.
   Mở được bằng link `/pickups?new=1` để CTA ở Tổng quan và trạng thái rỗng mở thẳng form (chỉ đổi giao diện, dùng API sẵn có).

### Đơn hàng
1. `PageHeader` + **[CTA] Tạo đơn** (chỉ tài xế); ô tìm kiếm; Làm mới.
2. Tab trạng thái có số đếm (giữ; cuộn ngang được trên điện thoại).
3. Bộ lọc: nhóm đồ, chỉ đơn trễ hạn (điện thoại: gom vào nút "Bộ lọc" – P2).
4. Danh sách: bảng (máy tính) / thẻ (điện thoại), phân trang máy chủ (giữ). Thẻ: mã + khách → trạng thái → nhóm đồ + hạn trả →
   thành tiền (vai trò được xem tiền) → nút thao tác 44px.
5. Drawer chi tiết + in phiếu giao nhận (giữ).
6. **Modal Tạo đơn / Báo sai lệch / Chi tiết & giá** – điện thoại toàn màn hình: thông tin khách (điền sẵn khi tạo từ yêu cầu lấy đồ)
   → số túi → danh sách dòng đồ giặt **xếp dọc** (chọn loại đồ rộng 100%, số lượng có nút +/−) → ghi chú → chân dính đáy với
   tóm tắt + **[CTA]** (sửa T3).

### Công nợ & thanh toán
1. `PageHeader` + **[CTA] Ghi nhận thanh toán** (xưởng) + "Xuất báo cáo" (phụ).
2. Chỉ số: một thẻ chia 4 cột (Tổng tiền, Đã thanh toán, Còn nợ, Nợ quá hạn).
3. Biểu đồ: doanh thu 6 tháng, tuổi nợ (đổi màu d30).
4. Công nợ theo khách: bảng (máy tính) / **thẻ mỗi khách** (điện thoại – sửa L1) với nút Sao kê, Lịch sử.
   Quản lý phía khách (chỉ một khách): hiển thị trực tiếp khối tuổi nợ + nút **Xem sao kê tháng** thay vì bảng một dòng.
5. Drawer lịch sử thanh toán, modal sao kê (in được), modal hủy thanh toán (giữ).

### Khách hàng (quản lý xưởng)
1. `PageHeader` + **[CTA] Thêm khách hàng**.
2. Tìm kiếm theo tên, địa chỉ và lọc loại khách (giữ, đã có).
3. Bảng (máy tính) / thẻ (điện thoại – P2). Cột thao tác thống nhất: Sửa · Tạo tài khoản · Ngừng / Kích hoạt (có xác nhận).
4. Modal sửa khách hàng; modal tạo tài khoản cho khách (giữ).

### Bảng giá (quản lý xưởng, kế toán)
1. `PageHeader` + **[CTA] Thêm mục giá**.
2. Tab: Bảng giá chung / Giá riêng theo khách (giữ).
3. Lọc theo nhóm đồ, dịch vụ (dùng dữ liệu đã tải).
4. Bảng; mục ẩn hiển thị mờ + nhãn "Đã ẩn".

### Tài khoản (quản lý xưởng)
1. `PageHeader` + **[CTA] Tạo tài khoản**.
2. Tìm kiếm tài khoản và lọc theo vai trò (giữ, đã có).
3. Bảng (máy tính) / thẻ (điện thoại – P2); khóa / mở khóa, đặt lại mật khẩu (có xác nhận, không tự khóa chính mình – giữ).

### Thông tin tài khoản
1. `PageHeader`.
2. Thông tin (một khối mô tả).
3. Đổi mật khẩu: form một cột ≤ 560px, ô nhập 16px trên điện thoại, **[CTA] Đổi mật khẩu**.

### Khung chung (MainLayout)
Sider phẳng; tiêu đề nhóm menu đạt tương phản (A2); header: ngữ cảnh (tên khách / xưởng + ngày) và menu người dùng;
thanh điều hướng dưới nhãn 12px (T4), có focus (A4); link bỏ qua tới nội dung (A6).

## 7. Mức độ ưu tiên P0 / P1 / P2

**P0 – sửa trước tiên** (ảnh hưởng truy cập hoặc luồng chính trên điện thoại):

| Mã | Việc | Trang |
|---|---|---|
| A1, S1, S5 | Bảng màu trạng thái một nguồn, đạt ≥ 4,5:1, có icon | Toàn bộ |
| T1 | Ô nhập 16px trên điện thoại | Toàn bộ form |
| T2 | Nút / ô nhập 44px trên điện thoại | Toàn bộ, ưu tiên thẻ đơn và thẻ lấy đồ |
| T3 | Modal Tạo đơn / Báo sai lệch toàn màn hình, dòng đồ giặt xếp dọc | Đơn hàng, Lấy đồ |
| A4 | Viền focus cho mọi phần tử bấm được | Toàn bộ |

**P1 – nên làm trong đợt này:**

| Mã | Việc | Trang |
|---|---|---|
| C1 | CTA "Yêu cầu lấy đồ" mở thẳng form (`/pickups?new=1`); khung giờ chọn bằng nút | Tổng quan, Lấy đồ |
| L1 | Công nợ dạng thẻ trên điện thoại; bố cục riêng cho quản lý phía khách | Công nợ |
| L3, A6 | `PageHeader` chung với `h1`; link bỏ qua; focus khi đổi trang | Toàn bộ |
| G1, G2, A3 | Bỏ gradient sider / băng chào; gộp thẻ ở Tổng quan | Tổng quan, khung chung |
| S2, S3 | Gộp token màu về `theme.ts`, bỏ hex cứng | Toàn bộ |
| A2, A5, T4 | Tương phản nhóm menu, tôn trọng giảm chuyển động, nhãn thanh dưới 12px | Khung chung |
| F1 | Trạng thái rỗng có hành động | Lấy đồ, Đơn hàng, Tổng quan |

**P2 – làm sau, khi P0/P1 ổn định:**

| Mã | Việc | Trang |
|---|---|---|
| L2 | Dạng thẻ trên điện thoại cho Khách hàng, Bảng giá, Tài khoản | Trang quản trị |
| S4 | Thay inline style bằng biến khoảng cách | Toàn bộ |
| A7 | Bỏ emoji | Tổng quan |
| – | Gom bộ lọc Đơn hàng vào nút "Bộ lọc" trên điện thoại | Đơn hàng |
| – | Lọc nhóm đồ / dịch vụ ở Bảng giá | Bảng giá |
| – | Tinh chỉnh trang đăng nhập (gradient một lớp, bố cục điện thoại) | Đăng nhập |

## 8. Chức năng không được làm hỏng

Sau mỗi bước triển khai phải kiểm tra lại các mục sau (bằng tài khoản demo, mật khẩu `11111111`).

**Xác thực và phân quyền**
- Đăng nhập, thông báo phiên hết hạn (`/login?expired=1`), tự đăng xuất khi nhận 401, đăng xuất.
- Menu, thanh điều hướng dưới, nút thao tác hiển thị đúng theo vai trò; `RoleRoute` chặn truy cập trang bằng URL.
- **Tài xế và nhân viên phía khách không thấy tiền** (cột thành tiền, tổng tiền, công nợ).

**Lấy đồ**
- Khách / quản lý xưởng tạo yêu cầu (ngày ≥ hôm nay, khung giờ, số túi, ghi chú); hủy yêu cầu kèm lý do.
- Tài xế nhận việc, trả lại việc; quản lý xưởng phân công (kể cả tài xế đã bị khóa vẫn hiện tên).
- "Đã đến · Tạo đơn" mở form tạo đơn điền sẵn khách và gắn với yêu cầu; yêu cầu chuyển "Đã lấy đồ".
- Link bản đồ và gọi điện trên thẻ.

**Đơn hàng**
- Tạo đơn (ít nhất một dòng; số lẻ chỉ cho đơn vị kg); báo sai lệch theo từng dòng, thêm đồ bị sót, bắt buộc lý do.
- Mọi bước chuyển trạng thái theo vai trò, kèm hộp xác nhận; nút bị khóa khi thiếu đơn giá (kèm lý do).
- Chi tiết & giá (đơn đang giao phải đủ giá), đổi hạn trả, hủy đơn (xóa mềm), xác nhận nhận đồ, báo thiếu/hỏng.
- Lọc / tìm kiếm / phân trang / sắp xếp phía máy chủ; link sâu `/orders?status=…`, `/orders?overdue=1`.
- Drawer chi tiết với nhật ký đơn; **in phiếu giao nhận** (`utils/print.ts`).

**Công nợ**
- Ghi nhận thanh toán (không vượt công nợ), hủy thanh toán kèm lý do, lịch sử thanh toán.
- Sao kê tháng và in sao kê; xuất 3 loại CSV.
- Biểu đồ doanh thu và tuổi nợ (kể cả dùng bàn phím, chú thích có số tiền).

**Quản trị**
- Thêm / sửa / ngừng – kích hoạt khách hàng; tạo tài khoản cho khách.
- Bảng giá chung, ẩn / hiện mục, giá riêng theo khách, bỏ giá riêng.
- Tạo tài khoản, khóa / mở khóa (không tự khóa mình), đặt lại mật khẩu; đổi mật khẩu của chính mình.

**Kỹ thuật**
- Định dạng tiếng Việt cho tiền, ngày giờ; locale `vi_VN` của antd.
- `npm run build` và `npm run lint` không phát sinh lỗi / cảnh báo mới; mỗi trang vẫn tải lười.
- Không đổi request / response API, không đổi `types.ts` ngoài phần hiển thị (nhãn, màu, icon).

## 9. Thứ tự triển khai

Mỗi bước là một commit riêng; cuối mỗi bước chạy build, lint và kiểm tra danh sách ở mục 8 với các vai trò liên quan.

| Bước | Nội dung | Mã xử lý | File chính |
|---|---|---|---|
| 0 | Chạy ứng dụng với dữ liệu demo, chụp màn hình các trang ở 375 / 768 / 1440px làm mốc | – | Không sửa code |
| 1 | **Nền tảng:** token một nguồn, bảng màu trạng thái, thang chữ / khoảng cách, chiều cao 44px và chữ 16px trên điện thoại, focus, giảm chuyển động, sider phẳng, nhóm menu, thanh dưới 12px | A1 A2 A4 A5 T1 T2 T4 S1 S2 S3 S5 G1 (sider) | `theme.ts`, `index.css`, `App.tsx`, `types.ts` (chỉ màu / icon), `MainLayout.tsx` |
| 2 | **Component dùng chung:** `PageHeader`, `StatusTag` / `PickupStatusTag`, `EmptyState`, modal form toàn màn hình trên điện thoại, `KpiCard` dùng token | L3 A6 F1 | `components/` |
| 3 | **Luồng điện thoại:** modal Tạo đơn / Báo sai lệch + `CatalogLines`; Lấy đồ (CTA mở thẳng form, khung giờ dạng nút); thẻ đơn hàng | T3 C1 F1 | `pages/orders/*`, `CatalogLines.tsx`, `Pickups.tsx`, `Orders.tsx` |
| 4 | **Tổng quan và Công nợ:** cấu trúc theo vai trò, gộp thẻ, bỏ gradient băng chào, Công nợ dạng thẻ trên điện thoại | G1 G2 A3 A7 L1 | `Dashboard.tsx`, `Finance.tsx`, `finance/*` |
| 5 | **Trang quản trị và còn lại:** `PageHeader` cho Khách hàng, Bảng giá, Tài khoản, Hồ sơ, Đăng nhập; (P2) thẻ trên điện thoại, bộ lọc | L2 S4 | Các trang còn lại |
| 6 | **Kiểm tra:** chụp lại so với bước 0; rà bàn phím và tương phản; cập nhật README phần giao diện | – | |

Bước 1–3 giải quyết toàn bộ P0 và phần P1 có tác động lớn nhất tới khách và tài xế; bước 4–5 hoàn tất P1 và P2.

## 10. Ngoài phạm vi

Các ý sau **không** thực hiện vì hệ thống chưa hỗ trợ; chỉ ghi lại để cân nhắc như chức năng mới trong tương lai:

- Website công khai / trang giới thiệu dịch vụ, đăng ký tài khoản, đặt lịch không cần đăng nhập.
- Khách tự chọn dịch vụ, xem bảng giá, báo giá trực tuyến (hiện chỉ xưởng xem bảng giá; tài xế chọn loại đồ khi đếm).
- Thanh toán trực tuyến, thông báo (email / SMS / push), đính kèm ảnh, chữ ký điện tử.
- Chế độ tối (token màu theo vai trò ở mục 5.1 giúp bổ sung sau này dễ hơn).
