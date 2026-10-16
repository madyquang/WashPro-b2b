#!/bin/sh
# Lệnh chính của container "backup": sao lưu mỗi ngày lúc BACKUP_TIME (HH:MM, giờ theo TZ, mặc định 02:00).
set -eu

TIME=${BACKUP_TIME:-02:00}
echo "Sao lưu tự động mỗi ngày lúc $TIME ($(date +%Z)), giữ ${BACKUP_KEEP_DAYS:-14} ngày, lưu tại ${BACKUP_OUT:-/backups}"

while true; do
    now=$(date +%s)
    next=$(date -d "$(date +%F) $TIME" +%s)
    if [ "$next" -le "$now" ]; then
        next=$(date -d "$(date +%F) $TIME tomorrow" +%s)
    fi
    sleep $((next - now))
    # Lỗi một lần (VD CSDL đang khởi động lại) không làm dừng lịch của các ngày sau
    sh /backup/backup.sh || echo "$(date '+%F %T') Sao lưu thất bại" >&2
done
