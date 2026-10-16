package com.washpro.washpro_b2b.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VietQrTest {

    @Test
    void crcMatchesStandardCheckValue() {
        // Giá trị kiểm tra chuẩn của CRC-16/CCITT-FALSE
        assertThat(VietQr.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void payloadFollowsVietQrStructure() {
        String p = VietQr.payload("970436", "0011 0040 12345", new BigDecimal("1250000.40"), "HD-202610-0007");
        assertThat(p).isEqualTo("000201" + "010212"
                // 38: NAPAS + (BIN, số tài khoản đã bỏ khoảng trắng) + dịch vụ chuyển tới tài khoản
                + "3857" + "0010A000000727" + "0127" + "0006970436" + "0113" + "0011004012345" + "0208QRIBFTTA"
                + "5303704" + "54071250000" + "5802VN" + "6218" + "0814HD-202610-0007" + "6304" + p.substring(p.length() - 4));
        assertThat(VietQr.crc16(p.substring(0, p.length() - 4))).isEqualTo(p.substring(p.length() - 4));
    }

    @Test
    void staticCodeWithoutAmountAndVietnameseContentIsStripped() {
        String p = VietQr.payload("970415", "123456789", null, "Thanh toán đơn #12 tháng mười");
        assertThat(p).contains("010211").doesNotContain("54");
        assertThat(VietQr.ascii("Thanh toán đơn #12 tháng mười")).isEqualTo("Thanh toan don 12 thang m");
    }

    @Test
    void invalidBankInfoIsRejected() {
        assertThatThrownBy(() -> VietQr.payload("9704", "123", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VietQr.payload("970436", "12-34", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pngDecodesBackToPayload() throws Exception {
        String p = VietQr.payload("970436", "0011004012345", new BigDecimal("150000"), "HD-202610-0007");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(VietQr.png(p, 300)));
        int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(img.getWidth(), img.getHeight(), pixels)));
        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(p);
    }
}
