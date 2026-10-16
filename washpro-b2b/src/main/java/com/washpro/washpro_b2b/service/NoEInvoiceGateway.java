package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.InvoiceDtos.InvoiceData;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Mặc định: chưa nối nhà cung cấp hóa đơn điện tử nào. */
@Component
public class NoEInvoiceGateway implements EInvoiceGateway {

    @Override
    public Optional<String> issue(InvoiceData invoice) {
        return Optional.empty();
    }
}
