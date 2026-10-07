package com.washpro.washpro_b2b.security;

import com.washpro.washpro_b2b.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptServiceTest {

    @Test
    void khoaSauKhiSaiQuaSoLanChoPhep() {
        LoginAttemptService service = new LoginAttemptService(3, 5);
        service.recordFailure("taixe01");
        service.recordFailure("taixe01");
        assertDoesNotThrow(() -> service.checkNotLocked("taixe01"));

        service.recordFailure("taixe01");
        assertThrows(ApiException.class, () -> service.checkNotLocked("TaiXe01"));
    }

    @Test
    void dangNhapDungThiXoaBoDemSai() {
        LoginAttemptService service = new LoginAttemptService(2, 5);
        service.recordFailure("admin");
        service.reset("admin");
        service.recordFailure("admin");
        assertDoesNotThrow(() -> service.checkNotLocked("admin"));
    }

    @Test
    void taiKhoanKhacKhongBiAnhHuong() {
        LoginAttemptService service = new LoginAttemptService(1, 5);
        service.recordFailure("a");
        assertDoesNotThrow(() -> service.checkNotLocked("b"));
    }
}
