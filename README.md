# Hệ thống quản lý giặt là B2B – THANHXUAN

Ứng dụng web quản lý hoạt động của một xưởng giặt là phục vụ khách hàng doanh nghiệp (nhà hàng, khách sạn,
homestay, spa, công ty, phòng khám). Hệ thống bao gồm: yêu cầu lấy đồ, đơn giặt theo từng loại đồ, bảng giá,
hạn trả đồ, xác nhận giao nhận, công nợ, thanh toán và báo cáo.

Tên mã nguồn và gói Java là `washpro` (tên ban đầu của dự án); tên hiển thị trên giao diện là THANHXUAN.

## Mục lục

1. [Công nghệ](#công-nghệ)
2. [Cấu trúc thư mục](#cấu-trúc-thư-mục)
3. [Cài đặt và chạy](#cài-đặt-và-chạy)
4. [Cấu hình](#cấu-hình)
5. [Dữ liệu mẫu](#dữ-liệu-mẫu)
6. [Vai trò và phân quyền](#vai-trò-và-phân-quyền)
7. [Nghiệp vụ](#nghiệp-vụ)
8. [API](#api)
9. [Kiểm thử](#kiểm-thử)
10. [Hạn chế hiện tại](#hạn-chế-hiện-tại)

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA / Hibernate, Bean Validation, Lombok |
| Cơ sở dữ liệu | MySQL 8 (cần MySQL ≥ 8.0.16 nếu dùng ràng buộc CHECK) |
| Frontend | React 19, TypeScript, Vite, Ant Design 6, React Router, Axios |
| Kiểm thử | JUnit 5, Spring MockMvc, H2 (test đơn vị và test tích hợp backend) |

## Cấu trúc thư mục

```
washpro-b2b/                     Backend (Maven)
  src/main/java/com/washpro/washpro_b2b/
    config/                      Tạo admin đầu tiên, bổ sung dữ liệu cũ, sinh dữ liệu demo
    controller/                  REST API (/api/...)
    dto/                         Dữ liệu vào/ra của API (không trả entity trực tiếp)
    entity/                      Thực thể JPA và enum (trạng thái, nhóm đồ, đơn vị...)
    repository/                  Spring Data JPA
    security/                    JWT, phân quyền, chống dò mật khẩu
    service/                     Quy tắc nghiệp vụ
  docs/                          Script SQL: nâng cấp CSDL cũ, bảng giá mẫu, xóa dữ liệu demo
washpro-b2b-frontend/            Frontend (Vite)
  src/pages/                     Các trang (đơn hàng, lấy đồ, khách hàng, bảng giá, công nợ...)
  src/components/                Bố cục chung, thẻ chỉ số, dòng đồ giặt, nhãn trạng thái/hạn trả
  src/theme.ts                   Màu sắc, font, cấu hình giao diện Ant Design
```

## Cài đặt và chạy

### Yêu cầu

- JDK 21 trở lên
- MySQL 8, đã tạo database rỗng `washpro_b2b`
- Node.js 20 trở lên

### Backend

```bash
cd washpro-b2b
cp .env.example .env          # điền DB_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_USERNAME/PASSWORD
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Backend chạy ở `http://localhost:8080`. Với `JPA_DDL_AUTO=update`, Hibernate tạo hoặc bổ sung bảng, cột khi khởi động.
Lần chạy đầu, nếu chưa có tài khoản Quản lý xưởng, hệ thống tạo tài khoản từ `BOOTSTRAP_ADMIN_USERNAME` /
`BOOTSTRAP_ADMIN_PASSWORD`. Các tài khoản khác do Quản lý xưởng tạo trong ứng dụng; không có API đăng ký công khai.

### Frontend

```bash
cd washpro-b2b-frontend
npm install
npm run dev                   # http://localhost:5173
```

Nếu backend không ở địa chỉ mặc định, tạo `.env` từ `.env.example` và sửa `VITE_API_URL`.
Bản build: `npm run build` (kết quả trong `dist/`).

### Nâng cấp từ cơ sở dữ liệu của phiên bản trước

Chạy `washpro-b2b/docs/nang-cap-csdl-cu.sql` một lần **trước** khi khởi động phiên bản mới. Script đổi kiểu cột
trạng thái và số lượng (Hibernate không tự đổi kiểu cột đã có) và bỏ ràng buộc duy nhất cũ của bảng giá.
Các thay đổi còn lại được xử lý tự động khi khởi động:

- Hibernate thêm các cột, bảng mới; dữ liệu cũ nhận giá trị mặc định (khách hàng loại "Nhà hàng",
  mục bảng giá thuộc nhóm "Khác", dịch vụ "Giặt sấy", đơn vị "cái").
- `LegacyDataRunner` gỡ các ràng buộc CHECK cũ trên cột trạng thái (không chứa trạng thái `DELIVERY_ISSUE`)
  và điền ngày hoàn thành cho đơn cũ từ nhật ký đơn.

## Cấu hình

Các biến môi trường đọc từ `washpro-b2b/.env` (xem `.env.example`):

| Biến | Bắt buộc | Mặc định | Ý nghĩa |
|---|---|---|---|
| `DB_URL` | | `jdbc:mysql://localhost:3306/washpro_b2b?...` | Chuỗi kết nối MySQL |
| `DB_USERNAME` | | `root` | Tài khoản MySQL |
| `DB_PASSWORD` | ✓ | | Mật khẩu MySQL |
| `JPA_DDL_AUTO` | | `update` | Chế độ quản lý cấu trúc bảng của Hibernate |
| `JWT_SECRET` | ✓ | | Khóa ký JWT, tối thiểu 32 ký tự |
| `JWT_EXPIRATION_MS` | | `28800000` (8 giờ) | Thời hạn phiên đăng nhập |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:5173` | Origin frontend được phép gọi API |
| `MAX_LOGIN_ATTEMPTS` / `LOGIN_LOCK_MINUTES` | | `5` / `5` | Số lần đăng nhập sai trước khi khóa tạm, thời gian khóa |
| `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` | Lần đầu | | Tài khoản Quản lý xưởng đầu tiên |
| `DEMO_DATA` | | `false` | `true` hoặc `reset` để sinh dữ liệu mẫu (xem bên dưới) |

## Dữ liệu mẫu

Đặt `DEMO_DATA=true` (sinh nếu chưa có) hoặc `DEMO_DATA=reset` (xóa dữ liệu demo cũ rồi sinh lại) và khởi động
backend một lần. Ví dụ trên PowerShell:

```powershell
$env:DEMO_DATA='reset'; .\mvnw.cmd spring-boot:run
```

Nội dung được sinh (`config/DemoDataRunner.java`, dùng hạt giống cố định nên lần nào cũng như nhau):

- 21 mục bảng giá (chỉ thêm mục chưa có) và vài giá riêng theo khách hàng.
- 8 khách hàng thuộc 5 loại, mỗi khách có thời gian trả đồ và kỳ thanh toán riêng.
- Khoảng 370 đơn trải đều 6 tháng gần nhất; phần lớn đã hoàn thành, số còn lại ở các trạng thái đang xử lý
  (gồm đơn trễ hạn, đơn có sai lệch, đơn sự cố giao trả).
- Thanh toán theo tháng với ba thói quen trả tiền (đúng hạn, chậm, nợ kéo dài) để có số liệu tuổi nợ; một khoản đã hủy.
- 12 yêu cầu lấy đồ ở các trạng thái.

Tài khoản demo, mật khẩu chung `11111111` (chỉ dùng để xem thử, không dùng trên hệ thống thật):

| Vai trò | Tên đăng nhập |
|---|---|
| Quản lý xưởng | Tài khoản tạo từ `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` (không thuộc dữ liệu demo) |
| Tài xế | `taixe.tai`, `taixe.hung`, `taixe.phuc` |
| Kế toán xưởng | `ketoan.mai` |
| Quản lý phía khách | `hoasen.ql`, `bienngoc.ql`, `bienxanh.ql`, `phoco.ql`, `ngoclan.ql`, `saomai.ql`, `antam.ql`, `doithong.ql` |
| Nhân viên phía khách | `hoasen.nv`, `bienxanh.nv` |

Dữ liệu demo chỉ thêm vào, không sửa dữ liệu khác. Xóa bằng `washpro-b2b/docs/xoa-du-lieu-demo.sql`
hoặc khởi động với `DEMO_DATA=reset`. Không bật `DEMO_DATA` trên hệ thống đang vận hành.

Ngoài ra, `washpro-b2b/docs/bang-gia-mau.sql` chỉ thêm bảng giá mẫu (không có khách hàng, đơn hàng).

## Vai trò và phân quyền

| Vai trò | Mã trong hệ thống | Quyền chính |
|---|---|---|
| Quản lý xưởng | `WORKSHOP_ADMIN` | Toàn bộ: khách hàng, tài khoản, bảng giá, điều phối đơn (giao, chốt, hủy, đổi hạn), phân công lấy đồ, công nợ |
| Kế toán xưởng | `WORKSHOP_ACCOUNTANT` | Bảng giá, chi tiết và đơn giá đơn hàng, ghi nhận/hủy thanh toán, công nợ, báo cáo |
| Tài xế | `DRIVER` | Nhận yêu cầu lấy đồ, tạo đơn, báo sai lệch trên đơn của mình |
| Quản lý phía khách | `RESTAURANT_MANAGER` | Như nhân viên phía khách, thêm công nợ, sao kê, lịch sử thanh toán, báo cáo của khách mình |
| Nhân viên phía khách | `RESTAURANT_STAFF` | Gửi yêu cầu lấy đồ, xác nhận số lượng, báo sai lệch, xác nhận nhận đồ hoặc báo sự cố |

Phân quyền gồm hai lớp: theo vai trò (`@PreAuthorize` ở controller) và theo dữ liệu (trong service). Tài xế chỉ
thấy đơn của mình; tài khoản phía khách chỉ thấy dữ liệu của khách mình; đơn ngoài phạm vi trả về 404.
Tài xế và nhân viên phía khách không được xem giá tiền trên giao diện.

Do lịch sử dự án, khái niệm "khách hàng" trong mã nguồn, bảng CSDL và API vẫn mang tên `restaurant`
(`Restaurant`, `restaurants`, `/api/restaurants`, `RESTAURANT_*`); loại khách được lưu ở cột `customer_type`.

## Nghiệp vụ

### Khách hàng

Mỗi khách hàng có: loại khách, địa chỉ, điện thoại, người liên hệ, **thời gian trả đồ** (giờ, tính từ lúc tài xế
nhận đồ) và **kỳ thanh toán** (số ngày được nợ sau khi đơn hoàn thành; 0 là thanh toán ngay). Khách ngừng hợp tác
không tạo được đơn mới nhưng giữ nguyên lịch sử.

### Bảng giá

- Mỗi mục giá gồm: tên loại đồ, nhóm đồ (đồ bàn ăn & bếp, chăn ga gối, khăn tắm & khăn mặt, đồng phục, khác),
  dịch vụ (giặt sấy, giặt + là ủi, giặt hấp, giặt + tẩy vết bẩn), đơn vị (cái, bộ, đôi, kg) và đơn giá.
  Tên + dịch vụ là duy nhất.
- Đơn vị kg cho phép số lượng thập phân (2 chữ số); các đơn vị khác chỉ nhận số nguyên.
- Giá riêng theo khách hàng ghi đè giá chung cho từng mục.
- Khi thêm dòng đồ giặt vào đơn, tên, nhóm, dịch vụ, đơn vị và đơn giá được sao chép vào đơn; thay đổi bảng giá
  sau đó không ảnh hưởng đơn đã có. Đơn giá được lấy tự động theo giá áp dụng cho khách.
- Đồ ngoài bảng giá được nhập tay (nhóm "Khác"); đơn giá do kế toán nhập.

### Yêu cầu lấy đồ

```
OPEN (chờ tài xế nhận) ─► ACCEPTED (tài xế đã nhận) ─► DONE (đã tạo đơn)
            └──────────────┴──► CANCELLED
```

- Tạo bởi tài khoản phía khách, hoặc Quản lý xưởng tạo hộ. Gồm ngày, khung giờ, số túi ước tính, ghi chú.
- Tài xế tự nhận việc hoặc được Quản lý xưởng phân công; tài xế có thể trả lại việc đã nhận.
- Khi tài xế tạo đơn từ yêu cầu, yêu cầu chuyển sang `DONE` và gắn với đơn.
- Khách hoặc Quản lý xưởng hủy được khi yêu cầu chưa hoàn tất.

### Đơn hàng

```
PENDING_CONFIRM ─► WASHING ─► DELIVERING ─► COMPLETED
     │   ▲                        │   ▲         ▲
     ▼   │                        ▼   │         │
  DISCREPANCY ─► WASHING      DELIVERY_ISSUE ───┘
```

| Trạng thái | Nhãn | Chuyển tiếp | Người thực hiện |
|---|---|---|---|
| `PENDING_CONFIRM` | Chờ xác nhận | → `WASHING` (xác nhận số lượng), → `DISCREPANCY` | Khách; tài xế/khách báo sai lệch |
| `DISCREPANCY` | Có sai lệch | → `WASHING`, → `PENDING_CONFIRM` (mở lại) | Khách; Quản lý xưởng |
| `WASHING` | Đang giặt | → `DELIVERING` | Quản lý xưởng |
| `DELIVERING` | Đang giao | → `COMPLETED` (khách xác nhận đã nhận đủ, hoặc xưởng chốt), → `DELIVERY_ISSUE` | Khách; Quản lý xưởng |
| `DELIVERY_ISSUE` | Sự cố giao trả | → `DELIVERING` (giao bù), → `COMPLETED` | Quản lý xưởng |
| `COMPLETED` | Hoàn thành | (không chuyển tiếp) | |

Quy tắc:

- Tài xế nhập số túi và số lượng từng loại đồ (ít nhất một dòng). Hạn trả = thời điểm tạo đơn + thời gian trả đồ của khách.
- Báo sai lệch theo từng dòng (0 = không có), có thể thêm loại đồ bị sót; bắt buộc nêu lý do.
- Chỉ chuyển sang `DELIVERING` hoặc `COMPLETED` khi mọi dòng đã có đơn giá. Khi đơn đang giao hoặc có sự cố,
  việc sửa chi tiết vẫn phải giữ đủ đơn giá.
- Đơn chưa hoàn thành và đã quá hạn trả được đánh dấu trễ hạn. Quản lý xưởng có thể đổi hạn trả (ghi nhật ký).
- Không cho nhảy bước hoặc quay lui; đơn đã hoàn thành không sửa được. Cập nhật trạng thái dùng khóa dòng
  (`PESSIMISTIC_WRITE`) để tránh hai thao tác đồng thời.
- Mọi thay đổi được ghi vào nhật ký đơn (người, thời điểm, nội dung). Hủy đơn là xóa mềm (`is_deleted`).
- Phiếu giao nhận in được từ chi tiết đơn, nhóm theo loại đồ.

### Công nợ và thanh toán

- Công nợ = tổng tiền các đơn hoàn thành − tổng các khoản thanh toán chưa hủy.
- Hạn thanh toán của mỗi đơn = ngày hoàn thành + kỳ thanh toán của khách. **Tuổi nợ** được tính bằng cách trừ
  tiền đã trả vào các đơn có hạn sớm nhất trước (FIFO), phần còn lại chia thành: chưa đến hạn, quá hạn 1–30,
  31–60, trên 60 ngày (`service/DebtAging.java`).
- Thanh toán không sửa hoặc xóa được; khoản nhập sai được đánh dấu hủy kèm lý do và người hủy.
  Số tiền thanh toán không được vượt công nợ hiện tại.
- **Sao kê tháng**: nợ đầu kỳ, các đơn hoàn thành và các khoản thanh toán trong tháng, nợ cuối kỳ; in được.
- Báo cáo CSV (UTF-8 có BOM, đã chặn CSV injection): công nợ theo khách, danh sách đơn, chi tiết từng dòng đồ giặt.
- Doanh thu theo tháng tính theo thời điểm đơn hoàn thành.

## API

Tất cả endpoint có tiền tố `/api`, trả JSON, yêu cầu header `Authorization: Bearer <token>` (trừ đăng nhập).
Lỗi trả về dạng `{ timestamp, status, message, errors? }`.

| Nhóm | Endpoint chính |
|---|---|
| Xác thực | `POST /auth/login`, `GET /auth/me`, `POST /auth/change-password` |
| Tài khoản | `GET/POST /users`, `PATCH /users/{id}/enabled`, `PUT /users/{id}/password` |
| Khách hàng | `GET/POST /restaurants`, `PUT /restaurants/{id}`, `PATCH /restaurants/{id}/active` |
| Bảng giá | `GET/POST /prices`, `PUT /prices/{id}`, `PATCH /prices/{id}/active`, `GET /prices/catalog`, `GET /prices/customers/{id}`, `PUT/DELETE /prices/customers/{id}/{priceId}` |
| Yêu cầu lấy đồ | `GET/POST /pickups`, `PUT /pickups/{id}/accept`, `/release`, `/assign`, `/cancel` |
| Đơn hàng | `GET/POST /orders` (xem bên dưới), `GET /orders/{id}`, `PUT /orders/{id}/approve`, `/discrepancy`, `/items`, `/status`, `/due`, `/confirm-receipt`, `/receipt-issue`, `DELETE /orders/{id}` |
| Thanh toán | `GET/POST /payments`, `PATCH /payments/{id}/void` |
| Báo cáo | `GET /dashboard`, `/reports/debts`, `/reports/revenue`, `/reports/statement`, `/reports/{debts,orders,items}/export` |

Quyền của từng endpoint được khai báo bằng `@PreAuthorize` trong các lớp ở `controller/`.

`GET /orders` lọc và phân trang ngay trong CSDL. Tham số (đều không bắt buộc):

| Tham số | Ý nghĩa |
|---|---|
| `status` | Một hoặc nhiều trạng thái, ngăn cách bằng dấu phẩy (`WASHING,DELIVERING`) |
| `overdue` | `true`: chỉ đơn trễ hạn |
| `category` | Nhóm đồ (`TABLE_LINEN`, `BEDDING`, `TOWEL`, `UNIFORM`, `OTHER`) |
| `q` | Tìm theo tên khách hàng, tài xế, loại đồ hoặc mã đơn (`#12`) |
| `sort` | `NEWEST` (mặc định, mới nhận trước) hoặc `DUE` (hạn trả gần nhất trước) |
| `page`, `size` | Trang (từ 0) và cỡ trang (1–100, mặc định 10) |

Kết quả: `{ content, total, page, size, counts, overdueCount }`; `counts` là số đơn theo từng trạng thái
(áp các bộ lọc khác, trừ trạng thái) để hiển thị trên các tab.

## Kiểm thử

```bash
cd washpro-b2b
./mvnw test
```

Hiện có 18 test đơn vị và 4 test tích hợp. Test tích hợp dùng H2 trong bộ nhớ (profile `it`,
`src/test/resources/application-it.properties`), không cần MySQL:

| Lớp test | Nội dung |
|---|---|
| `OrderApiIntegrationTest` | Gọi API thật qua JWT xuống CSDL: vòng đời đơn đến công nợ, phạm vi dữ liệu (404), phân quyền (401/403), lọc/phân trang/sắp xếp danh sách đơn |
| `OrderStatusTest` | Quy tắc chuyển trạng thái đơn |
| `PriceUnitTest` | Số lượng hợp lệ theo đơn vị, đọc đơn vị dạng chữ của dữ liệu cũ |
| `DebtAgingTest` | Phân nhóm tuổi nợ, trừ thanh toán theo FIFO |
| `LoginAttemptServiceTest` | Khóa tạm khi đăng nhập sai nhiều lần |
| `ReportServiceCsvTest` | Escape CSV và chặn CSV injection |

Frontend kiểm tra kiểu bằng `npm run build` (chạy `tsc`) và lint bằng `npm run lint`; chưa có test tự động cho giao diện.

## Hạn chế hiện tại

- Test tích hợp mới bao phủ luồng đơn hàng; chưa có cho lấy đồ, bảng giá, thanh toán. Chưa có test giao diện tự động.
- Cấu trúc CSDL do Hibernate (`ddl-auto=update`) quản lý, chưa dùng công cụ migration (Flyway/Liquibase);
  các bước nâng cấp phải thực hiện bằng script SQL và mã khởi động.
- JWT lưu trong `localStorage` của trình duyệt (dễ bị ảnh hưởng nếu có lỗ hổng XSS); chưa có refresh token.
- Bộ đếm đăng nhập sai lưu trong bộ nhớ, không chia sẻ được khi chạy nhiều máy chủ.
- Danh sách đơn đã phân trang phía máy chủ; trang tổng quan (`/dashboard`) và báo cáo/xuất CSV vẫn tải toàn bộ đơn
  trong phạm vi rồi tính trong bộ nhớ.
- Chưa có thông báo (email, SMS, push) khi có yêu cầu lấy đồ mới, đơn trễ hạn hoặc nợ quá hạn.
- Chưa hỗ trợ đính kèm ảnh (đồ hư hỏng, chữ ký khi giao nhận) và chưa xuất hóa đơn điện tử.
- Giao diện chỉ có chế độ sáng (màu đã tách theo vai trò trong `src/theme.ts` nên có thể bổ sung chế độ tối sau).
  Bản build đã tách gói theo trang (lần tải đầu khoảng 800 KB, gzip ~270 KB); phần lõi antd dùng chung vẫn là gói
  lớn nhất (~390 KB). Hệ thiết kế và trạng thái triển khai: `DESIGN_PLAN.md`.
- Tên trong mã nguồn (`restaurant`) không còn khớp với nghiệp vụ nhiều loại khách hàng.
