package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.InvoiceDtos.InvoiceData;

import java.util.Optional;

/**
 * Điểm nối với nhà cung cấp hóa đơn điện tử (VNPT, Viettel, MISA, BKAV...). Hiện chưa nối nhà cung cấp nào
 * (NoEInvoiceGateway): hóa đơn tháng chỉ xuất PDF.
 *
 * Khi có tài khoản của nhà cung cấp: viết một lớp cài đặt interface này (đánh dấu @Primary), chuyển InvoiceData
 * sang định dạng của họ và gọi API phát hành; rồi gọi issue(...) từ InvoiceService ở thời điểm phù hợp
 * (VD khi kế toán chốt hóa đơn tháng).
 */
public interface EInvoiceGateway {

    /** Phát hành hóa đơn điện tử; trả về mã tra cứu của nhà cung cấp, hoặc rỗng nếu chưa cấu hình. */
    Optional<String> issue(InvoiceData invoice);
}
