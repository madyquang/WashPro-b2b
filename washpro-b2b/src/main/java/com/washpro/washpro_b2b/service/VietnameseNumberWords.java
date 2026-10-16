package com.washpro.washpro_b2b.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Đọc số tiền thành chữ tiếng Việt cho dòng "Bằng chữ" trên hóa đơn, VD 1.250.000 → "Một triệu hai trăm năm mươi nghìn đồng". */
public final class VietnameseNumberWords {

    private static final String[] DIGITS = { "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín" };
    private static final String[] GROUPS = { "", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ" };

    private VietnameseNumberWords() {
    }

    /** Làm tròn tới đồng; số âm đọc kèm "Âm". */
    public static String money(BigDecimal amount) {
        long value = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        String words = read(Math.abs(value));
        String text = (value < 0 ? "âm " : "") + words + " đồng";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String read(long n) {
        if (n == 0) {
            return "không";
        }
        List<Integer> groups = new ArrayList<>();
        for (long v = n; v > 0; v /= 1000) {
            groups.add((int) (v % 1000));
        }
        if (groups.size() > GROUPS.length) {
            throw new IllegalArgumentException("Số quá lớn: " + n);
        }
        List<String> parts = new ArrayList<>();
        for (int i = groups.size() - 1; i >= 0; i--) {
            int g = groups.get(i);
            if (g == 0) {
                continue;
            }
            boolean leading = i == groups.size() - 1;
            parts.add(readGroup(g, leading) + (GROUPS[i].isEmpty() ? "" : " " + GROUPS[i]));
        }
        return String.join(" ", parts);
    }

    /** Một nhóm 3 chữ số. Nhóm không đứng đầu luôn đọc đủ hàng trăm ("không trăm linh năm"). */
    private static String readGroup(int g, boolean leading) {
        int h = g / 100;
        int t = (g / 10) % 10;
        int u = g % 10;
        List<String> w = new ArrayList<>();
        if (h > 0 || !leading) {
            w.add(DIGITS[h] + " trăm");
        }
        if (t == 0) {
            if (u > 0 && (h > 0 || !leading)) {
                w.add("linh");
            }
        } else if (t == 1) {
            w.add("mười");
        } else {
            w.add(DIGITS[t] + " mươi");
        }
        if (u > 0) {
            if (u == 1 && t > 1) {
                w.add("mốt");
            } else if (u == 5 && t > 0) {
                w.add("lăm");
            } else if (u == 4 && t > 1) {
                w.add("tư");
            } else {
                w.add(DIGITS[u]);
            }
        }
        return String.join(" ", w);
    }
}
