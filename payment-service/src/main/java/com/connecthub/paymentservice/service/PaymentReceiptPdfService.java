package com.connecthub.paymentservice.service;

import com.connecthub.paymentservice.entity.Payment;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Rectangle;
import com.lowagie.text.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
/**
 * Generates a human-readable PDF receipt for completed payment records.
 */
public class PaymentReceiptPdfService {

    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Value("${app.receipt.company-name:ConnectHub}")
    private String companyName;

    @Value("${app.receipt.support-email:support@connecthub.local}")
    private String supportEmail;

    public byte[] generateReceiptPdf(Payment payment) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document();

            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, new Color(16, 185, 129));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(55, 65, 81));
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(75, 85, 99));

            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            PdfPCell headerCell = new PdfPCell();
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPaddingBottom(20f);
            headerCell.addElement(new Paragraph(companyName, titleFont));
            headerCell.addElement(new Paragraph("Payment Receipt", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(55, 65, 81))));
            headerCell.addElement(new Paragraph("Date: " + (payment.getVerifiedAt() == null ? "Pending" : payment.getVerifiedAt().format(RECEIPT_DATE_FORMAT)), subtitleFont));
            headerTable.addCell(headerCell);
            document.add(headerTable);

            document.add(new Paragraph("Thank you for purchasing ConnectHub translation credits. Your transaction details are below.", subtitleFont));
            document.add(new Paragraph(" ", subtitleFont));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(15f);
            table.setSpacingAfter(15f);
            table.setWidths(new float[]{1.5f, 3.5f});

            addHeaderRow(table, "Transaction Details", headerFont);

            addRow(table, "Receipt No", safe(payment.getReceipt()), labelFont, valueFont, false);
            addRow(table, "Order ID", safe(payment.getOrderId()), labelFont, valueFont, true);
            addRow(table, "Payment ID", safe(payment.getPaymentId()), labelFont, valueFont, false);
            addRow(table, "Customer", safe(payment.getCustomerName()), labelFont, valueFont, true);
            addRow(table, "Email", safe(payment.getCustomerEmail()), labelFont, valueFont, false);
            addRow(table, "Plan", safe(payment.getPlanName()), labelFont, valueFont, true);
            addRow(table, "Credits", String.valueOf(payment.getCredits()), labelFont, valueFont, false);
            addRow(table, "Amount Paid", formatAmount(payment), labelFont, valueFont, true);
            addRow(table, "Currency", safe(payment.getCurrency()), labelFont, valueFont, false);
            addRow(table, "Status", safe(payment.getStatus()), labelFont, valueFont, true);

            document.add(table);

            PdfPTable footerTable = new PdfPTable(1);
            footerTable.setWidthPercentage(100);
            PdfPCell footerCell = new PdfPCell(new Paragraph("If you need help with this payment, contact " + supportEmail + ".", subtitleFont));
            footerCell.setBorder(Rectangle.TOP);
            footerCell.setBorderColor(Color.LIGHT_GRAY);
            footerCell.setPaddingTop(15f);
            footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            footerTable.addCell(footerCell);

            document.add(footerTable);
            document.close();

            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate payment receipt PDF", ex);
        }
    }

    private void addHeaderRow(PdfPTable table, String title, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(title, font));
        cell.setColspan(2);
        cell.setBackgroundColor(new Color(16, 185, 129));
        cell.setPadding(10f);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont, boolean alternateColor) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));

        labelCell.setPadding(10f);
        valueCell.setPadding(10f);

        labelCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorder(Rectangle.BOTTOM);

        labelCell.setBorderColor(new Color(229, 231, 235));
        valueCell.setBorderColor(new Color(229, 231, 235));

        if (alternateColor) {
            Color altColor = new Color(249, 250, 251);
            labelCell.setBackgroundColor(altColor);
            valueCell.setBackgroundColor(altColor);
        } else {
            labelCell.setBackgroundColor(Color.WHITE);
            valueCell.setBackgroundColor(Color.WHITE);
        }

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatAmount(Payment payment) {
        if (payment.getAmount() == null) {
            return "NA";
        }
        BigDecimal amount = BigDecimal.valueOf(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
        return safe(payment.getCurrency()) + " " + amount;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "NA" : value;
    }
}
