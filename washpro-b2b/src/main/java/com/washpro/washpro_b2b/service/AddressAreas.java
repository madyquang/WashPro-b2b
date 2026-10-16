package com.washpro.washpro_b2b.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lấy khu vực (quận / huyện) từ địa chỉ gõ tự do để gom các điểm dừng gần nhau, không cần API bản đồ.
 * VD "25 Nguyễn Huệ, Q.1, TP.HCM" → "Quận 1"; "15 Hàng Bè, Hoàn Kiếm, Hà Nội" → "Hoàn Kiếm";
 * "Thôn 2, Xã Yên Viên, Huyện Gia Lâm, Hà Nội" → "Gia Lâm". Không đoán được thì trả null.
 */
public final class AddressAreas {

    /**
     * Tiền tố quận / huyện / thị xã / thành phố thuộc tỉnh (so khớp trên chuỗi đã bỏ dấu, chữ thường).
     * Viết đủ thì cần khoảng trắng sau ("quan 1"); viết tắt thì cần dấu chấm hoặc khoảng trắng ("q.1", "q 1"),
     * hoặc dính liền số ("q1"), để "Hoàn Kiếm" không bị hiểu là "H. oàn Kiếm".
     */
    private static final Pattern DISTRICT = Pattern.compile(
            "^(?:(?:quan|huyen|thi xa|thanh pho)\\s+|(?:q|h|tx|tp)\\s*\\.\\s*|(?:q|h|tx|tp)\\s+|q(?=\\d))(.+)$");
    /** Phường / xã / thị trấn / thôn / ấp / tổ: cấp dưới quận, bỏ qua. */
    private static final Pattern WARD = Pattern.compile(
            "^(?:(?:phuong|xa|thi tran|thon|ap|to)\\s+|(?:p|tt)\\s*\\.\\s*|(?:p|tt)\\s+|p(?=\\d)).+$");

    private AddressAreas() {
    }

    public static String areaOf(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        List<String> parts = Arrays.stream(address.split("[,;\\n]")).map(String::trim).filter(s -> !s.isEmpty())
                .toList();
        // 1. Thành phần có tiền tố quận / huyện (bỏ thành phần cuối: thường là tỉnh / thành phố, VD "TP.HCM")
        for (int i = 0; i < parts.size() - 1 || (i == 0 && parts.size() == 1); i++) {
            String area = district(parts.get(i));
            if (area != null) {
                return area;
            }
        }
        // 2. Không có tiền tố: "số nhà đường, Quận, Thành phố" → thành phần áp chót (nếu không phải phường / xã)
        if (parts.size() >= 3) {
            String candidate = parts.get(parts.size() - 2);
            if (!WARD.matcher(plain(candidate)).matches()) {
                return candidate;
            }
        }
        return null;
    }

    private static String district(String part) {
        Matcher m = DISTRICT.matcher(plain(part));
        if (!m.matches()) {
            return null;
        }
        String rest = m.group(1).trim();
        if (rest.matches("\\d{1,2}")) {
            return "Quận " + Integer.parseInt(rest);
        }
        // Giữ nguyên tên có dấu: cắt phần tiền tố trên chuỗi gốc theo số ký tự của phần tên
        String original = part.trim();
        String name = original.substring(original.length() - rest.length()).trim();
        return name.isEmpty() ? null : name;
    }

    /** Bỏ dấu tiếng Việt, chữ thường (độ dài không đổi so với chuỗi gốc đã chuẩn hóa NFC). */
    private static String plain(String s) {
        String nfc = Normalizer.normalize(s.trim(), Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(nfc.length());
        for (char c : nfc.toCharArray()) {
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            sb.append(c == 'đ' || c == 'Đ' ? 'd' : base.isEmpty() ? c : base.charAt(0));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
