#!/bin/sh
# Khôi phục CSDL từ một bản sao lưu (GHI ĐÈ dữ liệu hiện tại) và chép lại ảnh đính kèm.
#   docker compose stop backend
#   docker compose run --rm backup sh /backup/restore.sh /backups/washpro-20261016-020000.sql.gz
#   docker compose start backend
set -eu

FILE=${1:?Cách dùng: restore.sh /backups/washpro-YYYYMMDD-HHMMSS.sql.gz}
DB=${DB_NAME:-washpro_b2b}
[ -f "$FILE" ] || { echo "Không thấy file $FILE" >&2; exit 1; }
gzip -t "$FILE"

echo "Khôi phục $FILE vào CSDL $DB (dữ liệu hiện tại sẽ bị thay thế)..."
mysql -h "${DB_HOST:-mysql}" -u "${DB_USER:-root}" -e "DROP DATABASE IF EXISTS \`$DB\`; CREATE DATABASE \`$DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
gunzip -c "$FILE" | mysql -h "${DB_HOST:-mysql}" -u "${DB_USER:-root}" --default-character-set=utf8mb4 "$DB"

if [ -d /uploads ] && [ -d "${BACKUP_OUT:-/backups}/uploads" ]; then
    cp -rn "${BACKUP_OUT:-/backups}/uploads/." /uploads/
fi
echo "Đã khôi phục. Khởi động lại backend: docker compose start backend"
