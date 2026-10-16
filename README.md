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
| Backend | Java 25 (LTS), Spring Boot 4.1, Spring Security (JWT), Spring Data JPA / Hibernate, Bean Validation, Lombok |
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

### Chạy nhanh bằng Docker

Chỉ cần Docker (kèm Docker Compose). Một lệnh dựng MySQL, backend và giao diện:

```bash
cp docker.env.example .env    # điền DB_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_USERNAME/PASSWORD
docker compose up -d --build  # lần đầu mất vài phút để build
```

Mở `http://localhost:3000` (đổi cổng bằng `APP_PORT`). Giao diện (nginx) chuyển các yêu cầu `/api` sang backend nên
không cần cấu hình CORS. Dữ liệu MySQL nằm trong volume `mysql-data`, giữ nguyên khi `docker compose down`
(xóa hẳn: `docker compose down -v`). Muốn có dữ liệu demo để xem thử: đặt `DEMO_DATA=true` trong `.env` trước lần chạy
đầu. Xem log: `docker compose logs -f backend`. Cập nhật phiên bản mới: `git pull` rồi `docker compose up -d --build`
(Flyway tự nâng cấp cấu trúc CSDL).

Cách chạy từng phần riêng lẻ (để phát triển) như dưới đây.

### Sao lưu và khôi phục

**Chạy bằng Docker:** dịch vụ `backup` tự sao lưu mỗi ngày lúc `BACKUP_TIME` (mặc định 02:00 giờ Việt Nam) vào thư mục
`BACKUP_DIR` trên máy chủ (mặc định `./backups`): CSDL dạng `washpro-<ngày>-<giờ>.sql.gz` (`mysqldump
--single-transaction`, không khóa bảng, ứng dụng vẫn chạy), ảnh đính kèm chép thêm vào `backups/uploads/`. Bản sao lưu
CSDL cũ hơn `BACKUP_KEEP_DAYS` ngày (mặc định 14) bị xóa. Nên chép thư mục này sang máy / ổ đĩa khác định kỳ.

```bash
docker compose exec backup sh /backup/backup.sh            # sao lưu ngay
docker compose logs backup                                  # xem lịch sử sao lưu

# Khôi phục (GHI ĐÈ dữ liệu hiện tại)
docker compose stop backend
docker compose run --rm backup sh /backup/restore.sh /backups/washpro-20261016-020000.sql.gz
docker compose start backend
```

**Chạy trực tiếp trên Windows:** `scripts\backup.ps1` đọc kết nối từ `washpro-b2b\.env`, tạo cùng định dạng
`.sql.gz` trong `backups\` (đổi bằng `-OutDir`, số ngày giữ `-KeepDays`). Đặt lịch chạy mỗi ngày bằng Task Scheduler:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
schtasks /Create /TN "WashPro sao luu" /SC DAILY /ST 02:00 /TR "powershell -NoProfile -ExecutionPolicy Bypass -File \"D:\duong-dan\scripts\backup.ps1\""
# Khôi phục (GHI ĐÈ, phải thêm -Yes); -Database để khôi phục ra CSDL khác xem thử
powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 -File backups\washpro-20261016-020000.sql.gz -Yes
```

CI chạy sao lưu ngay sau bộ E2E rồi khôi phục vào một CSDL khác bằng `backup/restore.sh` và so số đơn, số ảnh với CSDL
gốc, nên bản sao lưu luôn được kiểm tra là khôi phục được.

### Yêu cầu

- JDK 25 (LTS) trở lên
- MySQL 8, đã tạo database rỗng `washpro_b2b`
- Node.js 20 trở lên

### Backend

```bash
cd washpro-b2b
cp .env.example .env          # điền DB_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_USERNAME/PASSWORD
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Backend chạy ở `http://localhost:8080`. Khi khởi động, **Flyway** tạo / nâng cấp cấu trúc bảng theo các file trong
`washpro-b2b/src/main/resources/db/migration`; Hibernate chỉ kiểm tra bảng khớp với entity (`ddl-auto=validate`).
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

Cấu trúc CSDL do Flyway quản lý. Mỗi thay đổi là một file `V<số>__<mô tả>` trong `db/migration` (SQL, hoặc lớp Java
trong `src/main/java/db/migration` khi cần xử lý theo từng CSDL); Flyway ghi lại các bước đã chạy trong bảng
`flyway_schema_history` và chỉ chạy bước mới.

- **CSDL rỗng:** chạy từ `V1__khoi_tao.sql`.
- **CSDL đã có bảng từ trước khi dùng Flyway** (do Hibernate `ddl-auto=update` tạo): tự đánh dấu đã ở V1
  (`baseline-on-migrate`) rồi chạy `V2__chuan_hoa_csdl_cu`: gỡ các ràng buộc CHECK cũ trên cột trạng thái / loại
  (từng chặn trạng thái mới như `DELIVERY_ISSUE`), chuyển cột `users.role` sang `VARCHAR`, điền ngày hoàn thành cho
  đơn cũ từ nhật ký đơn. Không cần làm gì thêm.
- **CSDL của phiên bản rất cũ** (còn thiếu cột, ứng dụng báo lỗi `Schema-validation`): chạy
  `washpro-b2b/docs/nang-cap-csdl-cu.sql` một lần, khởi động **một lần** với `JPA_DDL_AUTO=update` để Hibernate thêm
  các cột còn thiếu, sau đó chạy bình thường.

Thêm thay đổi cấu trúc mới: tạo file `V<số tiếp theo>__<mô tả>.sql`; không sửa file migration đã chạy.

## Cấu hình

Các biến môi trường đọc từ `washpro-b2b/.env` (xem `.env.example`):

| Biến | Bắt buộc | Mặc định | Ý nghĩa |
|---|---|---|---|
| `DB_URL` | | `jdbc:mysql://localhost:3306/washpro_b2b?...` | Chuỗi kết nối MySQL |
| `DB_USERNAME` | | `root` | Tài khoản MySQL |
| `DB_PASSWORD` | ✓ | | Mật khẩu MySQL |
| `JPA_DDL_AUTO` | | `validate` | Hibernate chỉ kiểm tra cấu trúc bảng (Flyway quản lý); `update` chỉ dùng khi nâng cấp CSDL rất cũ |
| `JWT_SECRET` | ✓ | | Khóa ký JWT, tối thiểu 32 ký tự |
| `JWT_EXPIRATION_MS` | | `28800000` (8 giờ) | Thời hạn phiên đăng nhập |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:5173` | Origin frontend được phép gọi API |
| `MAX_LOGIN_ATTEMPTS` / `LOGIN_LOCK_MINUTES` | | `5` / `5` | Số lần đăng nhập sai trước khi khóa tạm, thời gian khóa |
| `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` | Lần đầu | | Tài khoản Quản lý xưởng đầu tiên |
| `DEMO_DATA` | | `false` | `true` hoặc `reset` để sinh dữ liệu mẫu (xem bên dưới) |
| `COMPANY_NAME`, `COMPANY_ADDRESS`, `COMPANY_PHONE`, `COMPANY_TAX_CODE` | | `Xưởng giặt là THANHXUAN` | Thông tin xưởng in trên hóa đơn tháng |
| `COMPANY_BANK_NAME`, `COMPANY_BANK_ACCOUNT`, `COMPANY_ACCOUNT_HOLDER` | | | Thông tin chuyển khoản in trên hóa đơn (không đặt thì không in) |
| `BACKUP_TIME` / `BACKUP_KEEP_DAYS` / `BACKUP_DIR` | | `02:00` / `14` / `./backups` | Lịch sao lưu tự động (chỉ khi chạy bằng Docker) |
| `UPLOADS_DIR` | | `uploads` | Thư mục lưu ảnh / chữ ký đính kèm đơn (Docker: volume `uploads`). Cần sao lưu cùng CSDL |
| `NOTIFICATION_DIGEST_CRON` | | `0 0 7 * * *` | Giờ gửi bản tin thông báo hằng ngày (cron Spring, giờ Việt Nam) |

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
| Quản lý phía khách | `CUSTOMER_MANAGER` | Như nhân viên phía khách, thêm công nợ, sao kê, lịch sử thanh toán, báo cáo của khách mình |
| Nhân viên phía khách | `CUSTOMER_STAFF` | Gửi yêu cầu lấy đồ, xác nhận số lượng, báo sai lệch, xác nhận nhận đồ hoặc báo sự cố |

Phân quyền gồm hai lớp: theo vai trò (`@PreAuthorize` ở controller) và theo dữ liệu (trong service). Tài xế chỉ
thấy đơn của mình; tài khoản phía khách chỉ thấy dữ liệu của khách mình; đơn ngoài phạm vi trả về 404.
Tài xế và nhân viên phía khách không được xem giá tiền trên giao diện.

Khách hàng (nhà hàng, khách sạn, spa, công ty...) mang tên `customer` thống nhất trong mã nguồn, CSDL và API
(`Customer`, bảng `customers`, cột `customer_id`, `/api/customers`, vai trò `CUSTOMER_*`); loại khách lưu ở cột
`customer_type`. Phiên bản trước dùng tên `restaurant`: migration `V4__doi_ten_khach_hang` tự đổi tên bảng / cột / mã vai
trò khi nâng cấp và đăng xuất mọi người dùng (đăng nhập lại là dùng được).

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

### Cài lên điện thoại (PWA)

Giao diện là ứng dụng web cài được (PWA): trên Android (Chrome / Edge) dùng nút **Cài ứng dụng** trong trang Tài khoản
hoặc menu tài khoản; trên iPhone mở bằng Safari → Chia sẻ → Thêm vào MH chính. Ứng dụng đã cài mở toàn màn hình, có
biểu tượng THANHXUAN và lối tắt Lấy đồ / Đơn hàng.

Service worker (`public/sw.js`, viết tay, không thêm thư viện; chỉ bật ở bản build):

- Lưu sẵn mọi gói JS / CSS ngay khi cài (danh sách lấy từ `dist/.vite/manifest.json`), trang lấy mạng trước, mất mạng
  thì dùng bản đã lưu: **mất sóng vẫn mở được ứng dụng** và các trang, kèm dải báo "Mất kết nối mạng".
- **Không lưu đệm API**: dữ liệu nghiệp vụ luôn lấy mới; khi mất mạng các thao tác báo lỗi bình thường, không có thao
  tác "chờ gửi" ngầm (tránh xác nhận / thu tiền hai lần).
- Bản cập nhật: nginx trả `sw.js` với `Cache-Control: no-cache` nên trình duyệt nhận phiên bản mới ở lần mở sau.

Không mở được một trang (VD mất mạng ngay lần cài đầu): hiện thông báo kèm nút Thử lại thay vì màn hình trắng.

### Lộ trình tài xế

Trang **Lộ trình** của tài xế (hôm nay / ngày mai) liệt kê các điểm cần đi: yêu cầu lấy đồ đã nhận hẹn tới ngày đó (kể
cả hẹn trước mà chưa lấy, gắn nhãn "Quá ngày hẹn") và các đơn đang giao trả (chỉ hôm nay). Không dùng API bản đồ trả
phí:

- **Gom theo khu vực**: quận / huyện đoán từ địa chỉ khách (`Q.1`, `Quận 3`, `Huyện Gia Lâm`, `TP. Thủ Đức`, hoặc thành
  phần áp chót như `Hoàn Kiếm` trong "15 Hàng Bè, Hoàn Kiếm, Hà Nội"). Trong mỗi khu vực: quá hẹn trước, rồi theo khung
  giờ. Địa chỉ không đoán được xếp cuối ("Chưa rõ khu vực").
- **Tự sắp thứ tự**: mũi tên lên / xuống trên từng điểm; thứ tự lưu trên máy của tài xế (theo ngày), có nút về thứ tự
  gợi ý.
- **Google Maps nhiều điểm dừng**: một nút mở chỉ đường qua mọi điểm theo thứ tự đang xếp (xuất phát từ vị trí hiện
  tại; tùy chọn kết thúc ở xưởng nếu đặt `COMPANY_ADDRESS`). Quá 10 điểm thì chia thành nhiều đoạn (giới hạn của Google
  Maps).

Quản lý xưởng xem được lộ trình của một tài xế qua API (`GET /routes?driverId=`).

### Hóa đơn tháng (PDF)

Trong hộp thoại **Sao kê** (trang Công nợ), nút **Hóa đơn PDF** tải hóa đơn tháng của khách: thông tin xưởng, khách
hàng, các dịch vụ trong tháng gộp theo loại đồ / dịch vụ / đơn giá, tổng tiền kèm số tiền bằng chữ, công nợ đầu kỳ /
phát sinh / đã thanh toán / còn phải trả (cùng số liệu với sao kê), thông tin chuyển khoản với nội dung là số hóa đơn
(`HD-<năm><tháng>-<mã khách>`), danh sách đơn để đối chiếu và chỗ ký xác nhận. Quản lý phía khách tải được hóa đơn của
khách mình. PDF nhúng phông Be Vietnam Pro (giấy phép SIL OFL, `src/main/resources/fonts/OFL.txt`).

Đây là bảng kê kiêm đề nghị thanh toán, **chưa phải hóa đơn điện tử** (chưa nối nhà cung cấp hóa đơn điện tử, chưa
tính thuế GTGT riêng). Điểm nối đã có sẵn: interface `EInvoiceGateway` (mặc định `NoEInvoiceGateway` không làm gì) nhận
`InvoiceData` (đủ thông tin người bán, người mua, từng dòng dịch vụ, tổng tiền). Khi có tài khoản VNPT / Viettel /
MISA..., viết một lớp cài đặt interface này (đánh dấu `@Primary`) gọi API của nhà cung cấp.

### Báo cáo hiệu suất

Trang **Hiệu suất** (quản lý xưởng, kế toán) và **Chất lượng dịch vụ** (quản lý phía khách, chỉ số của khách mình; vào
từ trang Công nợ trên điện thoại). Chọn tháng này / tháng trước / 30 ngày qua / khoảng tùy chọn (tối đa 366 ngày); tính
trên các đơn **nhận** trong khoảng đó (đơn đã hủy không tính):

| Chỉ số | Cách tính |
|---|---|
| Giao đúng hạn | Trong các đơn đã bắt đầu giao trả: lần chuyển sang "Đang giao" đầu tiên không muộn hơn hạn trả |
| Thời gian xử lý TB | Từ lúc tài xế nhận đồ (tạo đơn) tới lúc bắt đầu giao trả |
| Sai lệch số lượng | Đơn từng có báo sai lệch / số đơn nhận |
| Sự cố khi nhận | Đơn khách báo thiếu / hỏng / số đơn đã giao |
| Yêu cầu lấy đồ | Đã lấy / đã gửi trong kỳ, số đã hủy; chờ lấy TB: từ lúc khách gửi yêu cầu tới lúc tài xế tạo đơn |

Bảng theo khách hàng và theo tài xế (chỉ phía xưởng). Mỗi đơn chỉ lấy một dòng chỉ số từ CSDL (không tải chi tiết đơn).

### Ảnh và chữ ký giao nhận

Không bắt buộc, dùng làm bằng chứng khi có tranh chấp. Ảnh được thu nhỏ ngay trên điện thoại (cạnh dài 1600px,
thường 200–500 KB) trước khi gửi; máy chủ chỉ nhận JPG / PNG / WEBP (kiểm tra theo nội dung file), tối đa 8 MB mỗi
ảnh, 30 ảnh mỗi đơn.

| Loại | Ai đính kèm | Ở đâu trên giao diện |
|---|---|---|
| Ảnh lúc lấy đồ, chữ ký khách khi giao đồ bẩn | Tài xế, quản lý xưởng | Phần "Ảnh & chữ ký khách" khi tạo đơn; Chi tiết đơn |
| Ảnh lúc giao trả, chữ ký khách khi nhận đồ sạch | Tài xế, quản lý xưởng | Nút "Ảnh & chữ ký giao" (đơn đang giao); Chi tiết đơn |
| Ảnh đồ thiếu / hỏng | Phía khách, quản lý xưởng | Khi báo thiếu / hỏng; Chi tiết đơn |

Ai xem được đơn thì xem được ảnh của đơn (kế toán chỉ xem). Người tải lên tự xóa được ảnh của mình trong 24 giờ, sau
đó chỉ quản lý xưởng xóa được. File lưu trong `UPLOADS_DIR`, bảng `order_attachments` giữ thông tin file.

### Thông báo

Biểu tượng chuông trên thanh đầu trang hiện số thông báo chưa đọc (tự kiểm tra mỗi 30 giây và khi quay lại tab);
bấm vào một thông báo để mở đúng trang / đúng đơn. Ai nhận gì:

| Sự kiện | Người nhận |
|---|---|
| Khách (hoặc xưởng) gửi yêu cầu lấy đồ | Quản lý xưởng, mọi tài xế |
| Tài xế nhận việc / được phân công | Tài khoản phía khách; tài xế được phân công |
| Hủy yêu cầu lấy đồ | Quản lý xưởng, phía khách, tài xế đã nhận |
| Tài xế tạo đơn | Phía khách (xác nhận số lượng) |
| Báo sai lệch | Quản lý xưởng, phía khách, tài xế của đơn |
| Đơn chuyển sang Đang giao | Phía khách (kiểm đồ, xác nhận đã nhận) |
| Khách báo sự cố khi nhận đồ | Quản lý xưởng |
| Hủy đơn | Phía khách, tài xế của đơn |
| Ghi nhận thanh toán | Quản lý phía khách |
| Bản tin 7h sáng: đơn trễ hạn trả | Quản lý xưởng |
| Bản tin 7h sáng: nợ quá hạn | Quản lý xưởng, kế toán (tổng hợp); quản lý phía khách (nợ của mình) |

Người gây ra sự kiện không nhận thông báo của chính mình; tài khoản bị khóa không nhận. Bản tin mỗi loại gửi tối đa
một lần mỗi ngày cho mỗi người; thông báo cũ hơn 90 ngày được dọn tự động. Chưa gửi qua email / SMS / push.

## API

Tất cả endpoint có tiền tố `/api`, trả JSON, yêu cầu header `Authorization: Bearer <token>` (trừ đăng nhập).
Lỗi trả về dạng `{ timestamp, status, message, errors? }`.

| Nhóm | Endpoint chính |
|---|---|
| Xác thực | `POST /auth/login`, `GET /auth/me`, `POST /auth/change-password` (trả phiên mới), `POST /auth/logout-all` |
| Tài khoản | `GET/POST /users`, `PATCH /users/{id}/enabled`, `PUT /users/{id}/password` |
| Khách hàng | `GET/POST /customers`, `PUT /customers/{id}`, `PATCH /customers/{id}/active` |
| Bảng giá | `GET/POST /prices`, `PUT /prices/{id}`, `PATCH /prices/{id}/active`, `GET /prices/catalog`, `GET /prices/customers/{id}`, `PUT/DELETE /prices/customers/{id}/{priceId}` |
| Yêu cầu lấy đồ | `GET/POST /pickups`, `PUT /pickups/{id}/accept`, `/release`, `/assign`, `/cancel` |
| Đơn hàng | `GET/POST /orders` (xem bên dưới), `GET /orders/{id}`, `PUT /orders/{id}/approve`, `/discrepancy`, `/items`, `/status`, `/due`, `/confirm-receipt`, `/receipt-issue`, `DELETE /orders/{id}` |
| Thanh toán | `GET/POST /payments`, `PATCH /payments/{id}/void` |
| Ảnh / chữ ký | `GET/POST /orders/{id}/attachments` (multipart: `kind`, `file`), `GET /attachments/{id}/file`, `DELETE /attachments/{id}` |
| Thông báo | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |
| Lộ trình | `GET /routes?date=&driverId=` (tài xế xem của mình; quản lý xưởng truyền `driverId`) |
| Báo cáo | `GET /reports/invoice?customerId=&month=` (PDF), `GET /reports/performance?from=&to=`, `GET /dashboard`, `/reports/debts`, `/reports/revenue`, `/reports/statement`, `/reports/{debts,orders,items}/export` |

Quyền của từng endpoint được khai báo bằng `@PreAuthorize` trong các lớp ở `controller/`.

**Thu hồi phiên đăng nhập.** Mỗi tài khoản có số `token_version`, ghi vào token khi đăng nhập và được so lại ở mỗi
request. Đổi mật khẩu, quản lý xưởng đặt lại mật khẩu, hoặc bấm "Đăng xuất mọi thiết bị" (trang Tài khoản) sẽ tăng số
này: mọi token cũ hết hiệu lực ngay (thiết bị vừa đổi mật khẩu nhận token mới nên vẫn dùng tiếp). Khóa tài khoản và đổi
vai trò cũng có hiệu lực ngay vì quyền được nạp lại từ CSDL cho mỗi request.

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

Hiện có 18 test đơn vị và 7 test tích hợp. Test tích hợp dùng H2 trong bộ nhớ (profile `it`,
`src/test/resources/application-it.properties`), không cần MySQL:

| Lớp test | Nội dung |
|---|---|
| `OrderApiIntegrationTest` | Gọi API thật qua JWT xuống CSDL: vòng đời đơn đến công nợ, phạm vi dữ liệu (404), phân quyền (401/403), lọc/phân trang/sắp xếp danh sách đơn |
| `PaymentApiIntegrationTest` | Không thu vượt công nợ, kể cả hai người ghi nhận cùng lúc (khóa dòng khách hàng); khoản đã hủy không hủy lại được |
| `ReportApiIntegrationTest` | Số liệu tổng quan theo vai trò, công nợ và tuổi nợ (trả FIFO, kỳ thanh toán khác nhau), sao kê tháng, chặn thu vượt nợ; bỏ đơn đã hủy / khoản đã hủy / dòng chưa có giá, đơn cũ chưa có ngày hoàn thành |
| `RouteApiIntegrationTest` | Lộ trình: đúng điểm dừng (đã nhận, quá hẹn, đơn đang giao chỉ hôm nay; bỏ việc chưa nhận / đã xong / của tài xế khác), gom theo khu vực, quyền |
| `AddressAreasTest` | Đoán quận / huyện từ địa chỉ (Q.1, Q1, Quận 03, Huyện, TP. Thủ Đức, không nhầm "Hoàn Kiếm" / "Phú Nhuận" với tiền tố viết tắt) |
| `InvoiceApiIntegrationTest` | Hóa đơn tháng PDF: đọc lại chữ trong PDF để kiểm tra tên xưởng, số hóa đơn, khách, dòng dịch vụ gộp, tổng tiền, số tiền bằng chữ, công nợ; quản lý phía khách luôn nhận hóa đơn của mình; quyền, tháng không hợp lệ |
| `VietnameseNumberWordsTest` | Đọc số tiền bằng chữ (linh, mười, mốt, tư, lăm, không trăm, tỷ...) |
| `PerformanceApiIntegrationTest` | Báo cáo hiệu suất: đúng hạn / trễ hạn, sai lệch, sự cố nhận, giá trị đơn, đáp ứng lấy đồ; theo khách và tài xế; quản lý phía khách chỉ thấy khách mình; kiểm tra khoảng ngày và quyền |
| `AttachmentApiIntegrationTest` | Ảnh / chữ ký: loại được đính kèm theo vai trò, ngoài phạm vi đơn trả 404 (cả file), chỉ nhận ảnh thật, xóa cả file trên đĩa, chỉ người tải lên / quản lý xưởng được xóa |
| `NotificationApiIntegrationTest` | Đúng người nhận theo sự kiện (không gửi cho người gây ra, khách khác, tài khoản khóa), đọc / đọc hết, không đánh dấu được thông báo của người khác, bản tin hằng ngày không gửi trùng |
| `AuthApiIntegrationTest` | Đổi mật khẩu đăng xuất thiết bị khác nhưng giữ thiết bị đang dùng; đặt lại mật khẩu, đăng xuất mọi thiết bị thu hồi token; token cũ không có phiên bản bị từ chối |
| `OrderStatusTest` | Quy tắc chuyển trạng thái đơn |
| `PriceUnitTest` | Số lượng hợp lệ theo đơn vị, đọc đơn vị dạng chữ của dữ liệu cũ |
| `DebtAgingTest` | Phân nhóm tuổi nợ, trừ thanh toán theo FIFO |
| `LoginAttemptServiceTest` | Khóa tạm khi đăng nhập sai nhiều lần |
| `ReportServiceCsvTest` | Escape CSV và chặn CSV injection |

Frontend kiểm tra kiểu bằng `npm run build` (chạy `tsc`) và lint bằng `npm run lint`.

### Kiểm thử đầu-cuối (E2E) qua giao diện

`washpro-b2b-frontend/e2e/run.mjs` đăng nhập lần lượt từng vai trò qua form thật và đi hết các luồng nghiệp vụ
(59 bước, 9 module): đăng nhập / phân quyền, gửi và hủy yêu cầu lấy đồ, tài xế nhận việc, xem lộ trình và tạo đơn (kèm ảnh, chữ ký khách), khách xác nhận /
báo sai lệch, xưởng điều phối (mở lại, đổi hạn, giao, giao bù, hủy, in phiếu), khách nhận đồ / báo thiếu, kế toán nhập giá
/ thu tiền / hủy thu / sao kê / hóa đơn PDF / xuất CSV, báo cáo hiệu suất, quản lý khách hàng / tài khoản / bảng giá, thông báo (chuông báo việc mới, bấm mở đúng đơn), đổi mật khẩu (thiết bị khác bị đăng xuất), đăng xuất mọi thiết bị, PWA (manifest, icon; trên bản build: mất mạng vẫn mở được ứng dụng). Ngoài kết quả từng
bước, script còn bắt mọi lỗi API (mã ≥ 400 không mong đợi) và lỗi JavaScript trên trang.

```powershell
# Cần: MySQL + backend (8080) + frontend (5173) đang chạy, có dữ liệu demo (DEMO_DATA=true)
cd washpro-b2b-frontend
npm run test:e2e                     # tất cả module
npm run test:e2e -- auth,pickup      # chọn module
```

Script **ghi dữ liệu thật** vào CSDL (gắn nhãn `E2E-...`), chỉ chạy trên CSDL thử / dữ liệu demo. Trên Windows dùng sẵn
trình duyệt Edge; nơi khác chạy `npx playwright-core install chromium` một lần. Ảnh chụp bước lỗi nằm trong
`e2e/results/`. Biến môi trường: `E2E_APP_URL`, `E2E_API_URL`, `E2E_PASSWORD`, `E2E_BROWSER`, `E2E_PWA=1` (chỉ khi
chạy trên bản build có service worker, như CI).

### CI

`.github/workflows/ci.yml` chạy trên GitHub Actions mỗi lần push / pull request vào `main`: build và chạy 82 test
backend (H2, không cần MySQL), build + lint frontend, và dựng toàn bộ hệ thống bằng Docker Compose (dữ liệu demo)
rồi chạy bộ E2E trên đó, sau đó sao lưu và khôi phục thử; khi lỗi, ảnh chụp và log backend được lưu thành artifact `e2e-results`.

## Hạn chế hiện tại

- Test tích hợp backend bao phủ đơn hàng và thanh toán; lấy đồ, bảng giá được kiểm qua E2E. Migration Flyway chưa có
  test tự động (test H2 dựng bảng từ entity); đã thử trên MySQL với CSDL rỗng và bản sao CSDL đang dùng.
- JWT lưu trong `localStorage` của trình duyệt (dễ bị ảnh hưởng nếu có lỗ hổng XSS); chưa có refresh token
  (phiên hết hạn sau `JWT_EXPIRATION_MS`, nhưng có thể thu hồi sớm, xem mục API).
- Bộ đếm đăng nhập sai lưu trong bộ nhớ, không chia sẻ được khi chạy nhiều máy chủ.
- Danh sách đơn, trang tổng quan, công nợ / tuổi nợ và sao kê đều tính trong CSDL (COUNT / SUM / GROUP BY; công nợ
  gộp sẵn theo khách và ngày hoàn thành). Riêng xuất CSV danh sách đơn / chi tiết đồ giặt vẫn tải toàn bộ đơn trong
  phạm vi (bản chất là xuất hết dữ liệu).
- Thông báo chỉ có trong ứng dụng (kiểm tra định kỳ 30 giây), chưa gửi email / SMS / push.
- Hóa đơn tháng là PDF (bảng kê kiêm đề nghị thanh toán), chưa nối nhà cung cấp hóa đơn điện tử (đã có `EInvoiceGateway`).
- Giao diện chỉ có chế độ sáng (màu đã tách theo vai trò trong `src/theme.ts` nên có thể bổ sung chế độ tối sau).
  Bản build đã tách gói theo trang (lần tải đầu khoảng 800 KB, gzip ~270 KB); phần lõi antd dùng chung vẫn là gói
  lớn nhất (~390 KB). Hệ thiết kế và trạng thái triển khai: `DESIGN_PLAN.md`.
