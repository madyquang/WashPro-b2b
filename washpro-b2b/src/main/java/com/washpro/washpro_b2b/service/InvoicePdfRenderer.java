package com.washpro.washpro_b2b.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.washpro.washpro_b2b.dto.InvoiceDtos.Company;
import com.washpro.washpro_b2b.dto.InvoiceDtos.CustomerInfo;
import com.washpro.washpro_b2b.dto.InvoiceDtos.InvoiceData;
import com.washpro.washpro_b2b.dto.InvoiceDtos.Line;
import com.washpro.washpro_b2b.dto.InvoiceDtos.OrderRef;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dựng PDF khổ A4 cho hóa đơn tháng. Nhúng phông Be Vietnam Pro (giấy phép OFL) để hiển thị đúng tiếng Việt. */
@Component
public class InvoicePdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color BRAND = new Color(0x0B, 0x64, 0x66);
    private static final Color HEADER_BG = new Color(0xE0, 0xF2, 0xF2);
    private static final Color MUTED = new Color(0x4A, 0x57, 0x61);

    private final BaseFont regular;
    private final BaseFont bold;

    public InvoicePdfRenderer() {
        this.regular = font("fonts/BeVietnamPro-Regular.ttf");
        this.bold = font("fonts/BeVietnamPro-Bold.ttf");
    }

    private static BaseFont font(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            String name = path.substring(path.lastIndexOf('/') + 1);
            return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Không nạp được phông chữ " + path, e);
        }
    }

    public byte[] render(InvoiceData d) {
        Font title = new Font(bold, 16, Font.NORMAL, BRAND);
        Font h = new Font(bold, 10);
        Font n = new Font(regular, 9.5f);
        Font small = new Font(regular, 8.5f, Font.NORMAL, MUTED);
        Font smallItalic = new Font(regular, 8.5f, Font.ITALIC, MUTED);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Hóa đơn " + d.number());
            doc.addCreator(d.company().name());
            doc.open();

            // ----- Đầu trang: xưởng (trái) + tiêu đề, số hóa đơn (phải)
            PdfPTable top = table(new float[] { 55, 45 });
            PdfPCell left = cell();
            Company c = d.company();
            left.addElement(new Paragraph(c.name(), new Font(bold, 13, Font.NORMAL, BRAND)));
            addIf(left, c.address(), n);
            addIf(left, c.phone() == null ? null : "Điện thoại: " + c.phone(), n);
            addIf(left, c.taxCode() == null ? null : "Mã số thuế: " + c.taxCode(), n);
            top.addCell(left);
            PdfPCell right = cell();
            Paragraph t = new Paragraph("HÓA ĐƠN DỊCH VỤ", title);
            t.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(t);
            right.addElement(rightText("Tháng %02d/%d".formatted(d.month().getMonthValue(), d.month().getYear()), h));
            right.addElement(rightText("Số: " + d.number(), n));
            right.addElement(rightText("Ngày lập: " + DATE.format(d.issueDate()), n));
            top.addCell(right);
            doc.add(top);
            doc.add(spaced(new Paragraph("Bảng kê dịch vụ giặt là kiêm đề nghị thanh toán. Chứng từ này không thay thế "
                    + "hóa đơn giá trị gia tăng điện tử.", smallItalic), 4, 10));

            // ----- Khách hàng
            CustomerInfo cu = d.customer();
            PdfPTable info = table(new float[] { 22, 78 });
            row(info, "Khách hàng", cu.name() + " (" + cu.type() + ")", h, n);
            if (cu.address() != null) {
                row(info, "Địa chỉ", cu.address(), h, n);
            }
            String contact = join(cu.phone(), cu.contactName());
            if (!contact.isEmpty()) {
                row(info, "Liên hệ", contact, h, n);
            }
            row(info, "Kỳ thanh toán", cu.paymentTermDays() == 0 ? "Thanh toán ngay khi hoàn thành đơn"
                    : cu.paymentTermDays() + " ngày kể từ khi hoàn thành đơn", h, n);
            doc.add(info);

            // ----- Bảng dịch vụ
            doc.add(spaced(new Paragraph("Chi tiết dịch vụ trong tháng", h), 10, 4));
            PdfPTable lines = table(new float[] { 6, 44, 10, 12, 13, 15 });
            lines.setHeaderRows(1);
            for (String head : new String[] { "STT", "Nội dung", "ĐVT", "Số lượng", "Đơn giá", "Thành tiền" }) {
                PdfPCell hc = new PdfPCell(new Phrase(head, h));
                hc.setBackgroundColor(HEADER_BG);
                hc.setPadding(5);
                hc.setHorizontalAlignment(head.equals("Nội dung") ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                lines.addCell(hc);
            }
            int i = 1;
            for (Line l : d.lines()) {
                lines.addCell(boxed(String.valueOf(i++), n, Element.ALIGN_CENTER));
                lines.addCell(boxed(l.description(), n, Element.ALIGN_LEFT));
                lines.addCell(boxed(l.unit(), n, Element.ALIGN_CENTER));
                lines.addCell(boxed(quantity(l.quantity()), n, Element.ALIGN_RIGHT));
                lines.addCell(boxed(money(l.unitPrice()), n, Element.ALIGN_RIGHT));
                lines.addCell(boxed(money(l.amount()), n, Element.ALIGN_RIGHT));
            }
            if (d.lines().isEmpty()) {
                PdfPCell none = boxed("Không có đơn hoàn thành trong tháng", n, Element.ALIGN_CENTER);
                none.setColspan(6);
                lines.addCell(none);
            }
            PdfPCell totalLabel = boxed("Tổng tiền dịch vụ tháng", h, Element.ALIGN_RIGHT);
            totalLabel.setColspan(5);
            lines.addCell(totalLabel);
            lines.addCell(boxed(money(d.total()), h, Element.ALIGN_RIGHT));
            doc.add(lines);
            Paragraph words = new Paragraph(new Chunk("Bằng chữ: ", h));
            words.add(new Chunk(d.totalInWords() + ".", n));
            doc.add(spaced(words, 6, 8));

            // ----- Công nợ
            doc.add(spaced(new Paragraph("Công nợ", h), 6, 4));
            PdfPTable debt = table(new float[] { 25, 25, 25, 25 });
            for (String head : new String[] { "Nợ đầu kỳ", "Phát sinh trong tháng", "Đã thanh toán trong tháng",
                    "Còn phải thanh toán" }) {
                PdfPCell hc = boxed(head, h, Element.ALIGN_CENTER);
                hc.setBackgroundColor(HEADER_BG);
                debt.addCell(hc);
            }
            debt.addCell(boxed(money(d.openingBalance()), n, Element.ALIGN_RIGHT));
            debt.addCell(boxed(money(d.total()), n, Element.ALIGN_RIGHT));
            debt.addCell(boxed(money(d.paidInMonth()), n, Element.ALIGN_RIGHT));
            debt.addCell(boxed(money(d.closingBalance()), h, Element.ALIGN_RIGHT));
            doc.add(debt);

            if (c.bankAccount() != null) {
                String bank = "Chuyển khoản: " + join(c.bankName(), "số tài khoản " + c.bankAccount(),
                        c.accountHolder() == null ? null : "chủ tài khoản " + c.accountHolder())
                        + ". Nội dung chuyển khoản: " + d.number();
                doc.add(spaced(new Paragraph(bank, n), 8, 0));
            }

            // ----- Danh sách đơn trong tháng (đối chiếu)
            if (!d.orders().isEmpty()) {
                doc.add(spaced(new Paragraph("Các đơn hoàn thành trong tháng (" + d.orders().size() + " đơn)", h), 10, 4));
                PdfPTable list = table(new float[] { 1, 1, 1, 1, 1, 1 });
                for (OrderRef o : d.orders()) {
                    list.addCell(boxed("#" + o.id() + " · " + DATE.format(o.completedAt()).substring(0, 5), small,
                            Element.ALIGN_LEFT));
                    list.addCell(boxed(money(o.amount()), small, Element.ALIGN_RIGHT));
                }
                // Lấp ô trống cuối bảng (mỗi hàng 3 đơn)
                for (int k = (d.orders().size() % 3 == 0 ? 0 : 3 - d.orders().size() % 3) * 2; k > 0; k--) {
                    list.addCell(boxed("", small, Element.ALIGN_LEFT));
                }
                doc.add(list);
            }

            // ----- Ký xác nhận
            PdfPTable sign = table(new float[] { 50, 50 });
            sign.setSpacingBefore(18);
            for (String who : new String[] { "Khách hàng xác nhận", "Người lập" }) {
                PdfPCell s = cell();
                Paragraph p = new Paragraph(who, h);
                p.setAlignment(Element.ALIGN_CENTER);
                s.addElement(p);
                Paragraph hint = new Paragraph("(Ký, ghi rõ họ tên)", smallItalic);
                hint.setAlignment(Element.ALIGN_CENTER);
                s.addElement(hint);
                s.setMinimumHeight(70);
                sign.addCell(s);
            }
            doc.add(sign);
        } catch (DocumentException e) {
            throw new IllegalStateException("Không tạo được PDF hóa đơn", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    // ===================== Hàm hỗ trợ =====================

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    /** Ô không viền (bố cục). */
    private static PdfPCell cell() {
        PdfPCell c = new PdfPCell();
        c.setBorder(PdfPCell.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    /** Ô có viền (bảng số liệu). */
    private static PdfPCell boxed(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        c.setBorderColor(new Color(0xD0, 0xD7, 0xDC));
        return c;
    }

    private static void row(PdfPTable t, String label, String value, Font h, Font n) {
        PdfPCell l = new PdfPCell(new Phrase(label, h));
        l.setBorder(PdfPCell.NO_BORDER);
        l.setPaddingBottom(3);
        PdfPCell v = new PdfPCell(new Phrase(value, n));
        v.setBorder(PdfPCell.NO_BORDER);
        v.setPaddingBottom(3);
        t.addCell(l);
        t.addCell(v);
    }

    private static Paragraph rightText(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static void addIf(PdfPCell cell, String text, Font font) {
        if (text != null) {
            cell.addElement(new Paragraph(text, font));
        }
    }

    private static Paragraph spaced(Paragraph p, float before, float after) {
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
        return p;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                sb.append(sb.isEmpty() ? "" : " · ").append(p);
            }
        }
        return sb.toString();
    }

    static String money(BigDecimal v) {
        return String.format(Locale.US, "%,.0f", v.setScale(0, RoundingMode.HALF_UP)).replace(',', '.');
    }

    private static String quantity(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
