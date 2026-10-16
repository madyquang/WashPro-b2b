package com.washpro.washpro_b2b.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseNumberWordsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|không",
            "5|năm",
            "10|mười",
            "15|mười lăm",
            "21|hai mươi mốt",
            "24|hai mươi tư",
            "105|một trăm linh năm",
            "110|một trăm mười",
            "1005|một nghìn không trăm linh năm",
            "10000|mười nghìn",
            "1000000|một triệu",
            "1005000|một triệu không trăm linh năm nghìn",
            "1250000|một triệu hai trăm năm mươi nghìn",
            "23416000|hai mươi ba triệu bốn trăm mười sáu nghìn",
            "1000000000|một tỷ",
            "2000500000|hai tỷ năm trăm nghìn",
            "2050500000|hai tỷ không trăm năm mươi triệu năm trăm nghìn",
    })
    void readsNumbers(long n, String words) {
        assertThat(VietnameseNumberWords.read(n)).isEqualTo(words);
    }

    @Test
    void moneyIsRoundedAndCapitalized() {
        assertThat(VietnameseNumberWords.money(new BigDecimal("150000.40")))
                .isEqualTo("Một trăm năm mươi nghìn đồng");
        assertThat(VietnameseNumberWords.money(BigDecimal.ZERO)).isEqualTo("Không đồng");
    }
}
