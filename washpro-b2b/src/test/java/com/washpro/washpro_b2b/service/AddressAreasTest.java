package com.washpro.washpro_b2b.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AddressAreasTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "25 Nguyễn Huệ, Q.1, TP.HCM|Quận 1",
            "88 Võ Văn Kiệt, Q.5, TP.HCM|Quận 5",
            "12 Lê Lợi, Quận 03, TP Hồ Chí Minh|Quận 3",
            "123 Đường ABC, Phường 5, Quận 3, TP Hồ Chí Minh|Quận 3",
            "45 Điện Biên Phủ, Q. Bình Thạnh, TP.HCM|Bình Thạnh",
            "15 Hàng Bè, Hoàn Kiếm, Hà Nội|Hoàn Kiếm",
            "12 Lý Thái Tổ, Quận Hoàn Kiếm, Hà Nội|Hoàn Kiếm",
            "Thôn 2, Xã Yên Viên, Huyện Gia Lâm, Hà Nội|Gia Lâm",
            "200 Võ Văn Ngân, TP. Thủ Đức, TP.HCM|Thủ Đức",
            "KCN Tân Bình, P. Tây Thạnh, Hồ Chí Minh|",
            "10 Phan Xích Long, Phú Nhuận, TP.HCM|Phú Nhuận",
            "5 Trần Hưng Đạo, Q1, TP.HCM|Quận 1",
            "Số 9, H. Đông Anh, Hà Nội|Đông Anh",
    })
    void findsDistrict(String address, String area) {
        assertThat(AddressAreas.areaOf(address)).isEqualTo(area);
    }

    @Test
    void unknownWhenAddressTooVague() {
        assertThat(AddressAreas.areaOf("Bảo Khánh")).isNull();
        assertThat(AddressAreas.areaOf("45 Trần Phú, Đà Nẵng")).isNull();
        assertThat(AddressAreas.areaOf(null)).isNull();
        assertThat(AddressAreas.areaOf("  ")).isNull();
    }
}
