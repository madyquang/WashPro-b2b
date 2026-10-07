package com.washpro.washpro_b2b.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportServiceCsvTest {

    @Test
    void boChuoiTrongDauNhayKep() {
        assertEquals("\"Nhà hàng \"\"A\"\"\"", ReportService.csv("Nhà hàng \"A\""));
    }

    @Test
    void chanCsvInjection() {
        assertEquals("\"'=SUM(A1:A9)\"", ReportService.csv("=SUM(A1:A9)"));
        assertEquals("\"'@cmd\"", ReportService.csv("@cmd"));
    }

    @Test
    void giuNguyenSoAm() {
        assertEquals("\"-1500.00\"", ReportService.csv("-1500.00"));
    }

    @Test
    void giaTriNullThanhChuoiRong() {
        assertEquals("\"\"", ReportService.csv(null));
    }
}
