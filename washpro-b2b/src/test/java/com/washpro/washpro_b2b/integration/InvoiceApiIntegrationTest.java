package com.washpro.washpro_b2b.integration;

import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.washpro.washpro_b2b.entity.Customer;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Payment;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.CustomerRepository;
import com.washpro.washpro_b2b.repository.NotificationRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hóa đơn tháng PDF: đúng số liệu (khớp sao kê), đúng khách, quyền như sao kê. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class InvoiceApiIntegrationTest {

    private static final String PASSWORD = "Test@1234";

    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PickupRequestRepository pickupRepository;
    @Autowired
    CustomerPriceRepository customerPriceRepository;
    @Autowired
    NotificationRepository notificationRepository;

    Customer hoaSen;
    YearMonth lastMonth = YearMonth.now().minusMonths(1);

    @BeforeEach
    void seed() {
        notificationRepository.deleteAll();
        pickupRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerPriceRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();

        hoaSen = customerRepository.save(Customer.builder().name("Nhà hàng Hoa Sen").address("12 Lý Thái Tổ, Hà Nội")
                .paymentTermDays(15).build());
        Customer bienXanh = customerRepository.save(Customer.builder().name("Khách sạn Biển Xanh").build());
        user("ketoan", Role.WORKSHOP_ACCOUNTANT, null);
        user("bienxanh.ql", Role.CUSTOMER_MANAGER, bienXanh);
        User driver = user("taixe", Role.DRIVER, null);

        // Hai đơn hoàn thành tháng trước: cùng loại đồ, cùng giá -> gộp một dòng 15 cái x 10.000 = 150.000
        for (String qty : new String[] { "10", "5" }) {
            Order o = Order.builder().driver(driver).customer(hoaSen).status(OrderStatus.COMPLETED).totalBags(1)
                    .completedAt(lastMonth.atDay(10).atTime(9, 0)).build();
            o.addItem(OrderItem.builder().itemName("Khăn bàn").quantity(new BigDecimal(qty))
                    .unitPrice(new BigDecimal("10000")).build());
            orderRepository.save(o);
        }
        paymentRepository.save(Payment.builder().customer(hoaSen).amount(new BigDecimal("50000"))
                .paidDate(lastMonth.atDay(20)).createdBy("ketoan").build());
    }

    @Test
    void accountantDownloadsInvoiceMatchingStatement() throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/api/reports/invoice")
                .param("customerId", String.valueOf(hoaSen.getId())).param("month", lastMonth.toString())
                .header("Authorization", "Bearer " + login("ketoan")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(res.getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        String number = "HD-%d%02d-%04d".formatted(lastMonth.getYear(), lastMonth.getMonthValue(), hoaSen.getId());
        assertThat(res.getHeader("Content-Disposition")).contains("hoa-don-" + number.toLowerCase() + ".pdf");
        byte[] pdf = res.getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        String text = text(pdf);
        assertThat(text).contains("Xưởng giặt là THANHXUAN", "HÓA ĐƠN DỊCH VỤ", number, "Nhà hàng Hoa Sen", "12 Lý Thái Tổ, Hà Nội",
                "15 ngày kể từ khi hoàn thành đơn", "Khăn bàn", "150.000",
                "Một trăm năm mươi nghìn đồng", "100.000");
    }

    @Test
    void paymentQrCarriesRemainingAmountAndInvoiceNumber() throws Exception {
        String number = "HD-%d%02d-%04d".formatted(lastMonth.getYear(), lastMonth.getMonthValue(), hoaSen.getId());
        String body = mvc.perform(get("/api/reports/payment-qr")
                .param("customerId", String.valueOf(hoaSen.getId())).param("month", lastMonth.toString())
                .header("Authorization", "Bearer " + login("ketoan")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Double>read(body, "$.amount")).isEqualTo(100000.0);
        assertThat(JsonPath.<String>read(body, "$.content")).isEqualTo(number);
        String image = JsonPath.read(body, "$.image");
        assertThat(image).startsWith("data:image/png;base64,");

        // Ảnh QR giải mã ra đúng dữ liệu VietQR: ngân hàng, tài khoản, số tiền, nội dung
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(image.substring("data:image/png;base64,".length()))));
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        String payload = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(img.getWidth(), img.getHeight(), px)))).getText();
        assertThat(payload).contains("A000000727", "970436", "0011004012345", "5406100000", number);

        // Hóa đơn PDF in kèm mã QR
        byte[] pdf = mvc.perform(get("/api/reports/invoice")
                .param("customerId", String.valueOf(hoaSen.getId())).param("month", lastMonth.toString())
                .header("Authorization", "Bearer " + login("ketoan")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(text(pdf)).contains("Quét mã bằng ứng dụng ngân hàng");

        // Không còn nợ: không có mã QR
        mvc.perform(get("/api/reports/payment-qr").param("month", lastMonth.toString())
                .header("Authorization", "Bearer " + login("bienxanh.ql")))
                .andExpect(status().isNoContent());
    }

    @Test
    void customerManagerAlwaysGetsOwnInvoice() throws Exception {
        byte[] pdf = mvc.perform(get("/api/reports/invoice")
                .param("customerId", String.valueOf(hoaSen.getId())).param("month", lastMonth.toString())
                .header("Authorization", "Bearer " + login("bienxanh.ql")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String text = text(pdf);
        assertThat(text).contains("Khách sạn Biển Xanh", "Không có đơn hoàn thành trong tháng");
        assertThat(text).doesNotContain("Hoa Sen");
    }

    @Test
    void accessAndMonthAreChecked() throws Exception {
        mvc.perform(get("/api/reports/invoice").param("customerId", String.valueOf(hoaSen.getId()))
                .param("month", lastMonth.toString()).header("Authorization", "Bearer " + login("taixe")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/invoice").param("customerId", String.valueOf(hoaSen.getId()))
                .param("month", YearMonth.now().plusMonths(1).toString())
                .header("Authorization", "Bearer " + login("ketoan")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/invoice").param("customerId", String.valueOf(hoaSen.getId()))
                .param("month", "10-2026").header("Authorization", "Bearer " + login("ketoan")))
                .andExpect(status().isBadRequest());
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                sb.append(extractor.getTextFromPage(p)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private User user(String username, Role role, Customer customer) {
        return userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(username)
                .role(role)
                .customer(customer)
                .build());
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
