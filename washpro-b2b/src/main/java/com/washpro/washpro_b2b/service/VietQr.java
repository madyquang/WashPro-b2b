package com.washpro.washpro_b2b.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;

/**
 * Mã QR chuyển khoản theo chuẩn VietQR (NAPAS 247, định dạng EMVCo): mọi ứng dụng ngân hàng Việt Nam quét được,
 * tự điền số tài khoản, số tiền và nội dung. Tự dựng chuỗi dữ liệu, không gọi dịch vụ bên ngoài.
 */
public final class VietQr {

    /** Mã định danh NAPAS cho dịch vụ chuyển khoản nhanh tới tài khoản. */
    private static final String NAPAS_GUID = "A000000727";
    private static final String TRANSFER_TO_ACCOUNT = "QRIBFTTA";

    private VietQr() {
    }

    /**
     * @param bankBin   mã BIN 6 số của ngân hàng nhận (VD Vietcombank 970436)
     * @param account   số tài khoản nhận
     * @param amount    số tiền (đồng); null = để người chuyển tự nhập
     * @param content   nội dung chuyển khoản (bỏ dấu, tối đa 25 ký tự để mọi ngân hàng giữ nguyên)
     */
    public static String payload(String bankBin, String account, BigDecimal amount, String content) {
        if (bankBin == null || !bankBin.matches("\\d{6}")) {
            throw new IllegalArgumentException("Mã BIN ngân hàng phải gồm 6 chữ số");
        }
        String acc = account.replaceAll("\\s", "");
        if (!acc.matches("[0-9A-Za-z]{1,19}")) {
            throw new IllegalArgumentException("Số tài khoản không hợp lệ");
        }
        String beneficiary = field("00", bankBin) + field("01", acc);
        String merchant = field("00", NAPAS_GUID) + field("01", beneficiary) + field("02", TRANSFER_TO_ACCOUNT);
        boolean hasAmount = amount != null && amount.signum() > 0;

        StringBuilder sb = new StringBuilder()
                .append(field("00", "01"))
                // 11: mã tĩnh (người chuyển nhập số tiền), 12: mã động (đã có số tiền)
                .append(field("01", hasAmount ? "12" : "11"))
                .append(field("38", merchant))
                .append(field("53", "704"));
        if (hasAmount) {
            sb.append(field("54", amount.setScale(0, RoundingMode.HALF_UP).toPlainString()));
        }
        sb.append(field("58", "VN"));
        String note = ascii(content);
        if (!note.isEmpty()) {
            sb.append(field("62", field("08", note)));
        }
        sb.append("6304");
        return sb + crc16(sb.toString());
    }

    /** Ảnh PNG của mã QR (nền trắng, chừa lề để máy quét nhận dạng). */
    public static byte[] png(String payload, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalArgumentException("Không tạo được mã QR", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String field(String id, String value) {
        int len = value.getBytes(StandardCharsets.UTF_8).length;
        if (len > 99) {
            throw new IllegalArgumentException("Trường " + id + " quá dài");
        }
        return id + String.format("%02d", len) + value;
    }

    /** Nội dung chuyển khoản: bỏ dấu tiếng Việt, chỉ giữ chữ, số, khoảng trắng, gạch; tối đa 25 ký tự. */
    static String ascii(String s) {
        if (s == null) {
            return "";
        }
        String plain = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').replaceAll("[^0-9A-Za-z \\-]", "").trim();
        return plain.length() > 25 ? plain.substring(0, 25).trim() : plain;
    }

    /** CRC-16/CCITT-FALSE (đa thức 0x1021, giá trị đầu 0xFFFF) theo chuẩn EMVCo, 4 chữ số hex in hoa. */
    static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
