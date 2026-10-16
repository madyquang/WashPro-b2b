#!/bin/sh
# Sao lưu một lần: CSDL (mysqldump, nén gzip) + ảnh / chữ ký đính kèm đơn; xóa bản sao lưu CSDL quá hạn giữ.
# Chạy trong container "backup" (docker-compose.yml). Sao lưu ngay: docker compose exec backup sh /backup/backup.sh
set -eu

OUT=${BACKUP_OUT:-/backups}
KEEP=${BACKUP_KEEP_DAYS:-14}
DB=${DB_NAME:-washpro_b2b}
STAMP=$(date +%Y%m%d-%H%M%S)
FILE="$OUT/washpro-$STAMP.sql.gz"

mkdir -p "$OUT/uploads"

# --single-transaction: ảnh chụp nhất quán mà không khóa bảng (InnoDB), ứng dụng vẫn chạy bình thường.
# Ghi ra file tạm rồi mới đổi tên: không bao giờ để lại file sao lưu dở dang mang tên hợp lệ.
mysqldump -h "${DB_HOST:-mysql}" -u "${DB_USER:-root}" --single-transaction --routines --triggers \
    --set-gtid-purged=OFF --default-character-set=utf8mb4 "$DB" | gzip > "$FILE.part"
mv "$FILE.part" "$FILE"

# Ảnh đính kèm không bao giờ bị sửa (tên file ngẫu nhiên), chỉ thêm / xóa: chép thêm file mới vào bản sao,
# giữ cả file đã xóa trên hệ thống (để còn khôi phục được).
if [ -d /uploads ]; then
    cp -rn /uploads/. "$OUT/uploads/"
fi

find "$OUT" -maxdepth 1 -name 'washpro-*.sql.gz' -mtime +"$KEEP" -delete
find "$OUT" -maxdepth 1 -name 'washpro-*.sql.gz.part' -mmin +60 -delete

echo "$(date '+%F %T') Đã sao lưu $FILE ($(du -h "$FILE" | cut -f1)); giữ $KEEP ngày"
