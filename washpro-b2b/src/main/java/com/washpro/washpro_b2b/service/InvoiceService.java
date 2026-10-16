package com.washpro.washpro_b2b.service;

import com.washpro.washpro_b2b.dto.FinanceDtos.StatementResponse;
import com.washpro.washpro_b2b.dto.InvoiceDtos.Company;
import com.washpro.washpro_b2b.dto.InvoiceDtos.CustomerInfo;
import com.washpro.washpro_b2b.dto.InvoiceDtos.InvoiceData;
import com.washpro.washpro_b2b.dto.InvoiceDtos.Line;
import com.washpro.washpro_b2b.dto.InvoiceDtos.OrderRef;
import com.washpro.washpro_b2b.dto.InvoiceDtos.PaymentQr;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.exception.ApiException;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Hóa đơn tháng của một khách: các đơn hoàn thành trong tháng gộp theo dòng dịch vụ, kèm công nợ đầu / cuối kỳ
 * (lấy từ sao kê nên luôn khớp số trên trang Công nợ). Quyền xem như sao kê: quản lý phía khách chỉ xem của mình.
 */
@Service
public class InvoiceService {

    private final ReportService reportService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InvoicePdfRenderer renderer;
    private final Company company;

    public InvoiceService(ReportService reportService, OrderRepository orderRepository,
            CustomerRepository customerRepository, InvoicePdfRenderer renderer,
            @Value("${app.company.name}") String name, @Value("${app.company.address:}") String address,
            @Value("${app.company.phone:}") String phone, @Value("${app.company.tax-code:}") String taxCode,
            @Value("${app.company.bank-account:}") String bankAccount,
            @Value("${app.company.bank-name:}") String bankName,
            @Value("${app.company.account-holder:}") String accountHolder,
            @Value("${app.company.bank-bin:}") String bankBin) {
        this.reportService = reportService;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.renderer = renderer;
        this.company = new Company(name, blankToNull(address), blankToNull(phone), blankToNull(taxCode),
                blankToNull(bankAccount), blankToNull(bankName), blankToNull(accountHolder), blankToNull(bankBin));
    }

    public record InvoicePdf(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public InvoicePdf pdf(Long customerId, YearMonth month) {
        InvoiceData data = build(customerId, month);
        String file = "hoa-don-" + data.number().toLowerCase() + ".pdf";
        return new InvoicePdf(file, renderer.render(data));
    }

    @Transactional(readOnly = true)
    public InvoiceData build(Long customerId, YearMonth month) {
        if (month.isAfter(YearMonth.now())) {
            throw ApiException.badRequest("Chưa tới tháng này");
        }
        // Sao kê kiểm tra quyền (quản lý phía khách luôn lấy khách của mình) và cho số dư đầu / cuối kỳ
        StatementResponse st = reportService.statement(customerId, month);
        Customer c = customerRepository.findById(st.customerId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));

        List<Order> orders = orderRepository.findCompletedBetween(c.getId(), month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
        Map<String, Line> lines = new LinkedHashMap<>();
        for (Order o : orders) {
            for (OrderItem i : o.getItems()) {
                BigDecimal price = i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice();
                String description = i.getItemName() + " · " + i.getServiceType().getLabel();
                String unit = i.getUnit().getLabel();
                String key = description + "|" + unit + "|" + price.stripTrailingZeros().toPlainString();
                Line old = lines.get(key);
                BigDecimal qty = (old == null ? BigDecimal.ZERO : old.quantity()).add(i.getQuantity());
                lines.put(key, new Line(description, unit, qty, price, qty.multiply(price)));
            }
        }
        List<Line> sorted = new ArrayList<>(lines.values());
        sorted.sort(Comparator.comparing(Line::description).thenComparing(Line::unitPrice));

        List<OrderRef> refs = st.orders().stream().map(o -> new OrderRef(o.id(), o.completedAt(), o.amount())).toList();
        CustomerInfo customer = new CustomerInfo(c.getId(), c.getName(), c.getCustomerType().getLabel(),
                c.getAddress(), c.getPhone(), c.getContactName(), c.getPaymentTermDays());
        String number = "HD-%d%02d-%04d".formatted(month.getYear(), month.getMonthValue(), c.getId());
        return new InvoiceData(number, month, LocalDate.now(), company, customer, sorted, refs, st.totalBilled(),
                VietnameseNumberWords.money(st.totalBilled()), st.openingBalance(), st.totalPaid(),
                st.closingBalance(), qrPayload(st.closingBalance(), number));
    }

    /** Mã QR chuyển khoản số còn phải thanh toán của tháng (nội dung = số hóa đơn); rỗng nếu không cần / chưa cấu hình. */
    @Transactional(readOnly = true)
    public Optional<PaymentQr> paymentQr(Long customerId, YearMonth month) {
        InvoiceData d = build(customerId, month);
        if (d.paymentQr() == null) {
            return Optional.empty();
        }
        String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(VietQr.png(d.paymentQr(), 360));
        return Optional.of(new PaymentQr(d.closingBalance(), d.number(), company.bankName(), company.bankAccount(),
                company.accountHolder(), image));
    }

    private String qrPayload(BigDecimal amount, String content) {
        if (company.bankBin() == null || company.bankAccount() == null || amount.signum() <= 0) {
            return null;
        }
        return VietQr.payload(company.bankBin(), company.bankAccount(), amount, content);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
