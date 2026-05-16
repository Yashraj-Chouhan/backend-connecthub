package com.connecthub.paymentservice.service;

import com.connecthub.paymentservice.entity.Payment;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * Sends a payment receipt email with the generated PDF attached after a
 * successful top-up.
 */
public class PaymentReceiptEmailService {

    private final JavaMailSender mailSender;
    private final PaymentReceiptPdfService paymentReceiptPdfService;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public void sendReceipt(Payment payment) {
        if (!StringUtils.hasText(payment.getCustomerEmail())) {
            log.warn("Skipping receipt email because customer email is not available for order {}", payment.getOrderId());
            return;
        }

        if (!StringUtils.hasText(fromEmail)) {
            log.warn("Mock receipt delivery for {} on order {}", payment.getCustomerEmail(), payment.getOrderId());
            log.warn("Please configure SMTP details in payment-service application settings to enable actual receipt delivery.");
            return;
        }

        try {
            byte[] pdfBytes = paymentReceiptPdfService.generateReceiptPdf(payment);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom(fromEmail);
            helper.setTo(payment.getCustomerEmail());
            helper.setSubject("ConnectHub Payment Receipt - " + payment.getReceipt());
            helper.setText(buildEmailBody(payment), true);
            helper.addAttachment(buildAttachmentName(payment), new ByteArrayResource(pdfBytes));

            mailSender.send(mimeMessage);
            log.info("Payment receipt email sent to {} for order {}", payment.getCustomerEmail(), payment.getOrderId());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to send payment receipt email", ex);
        }
    }

    private String buildAttachmentName(Payment payment) {
        String suffix = StringUtils.hasText(payment.getReceipt()) ? payment.getReceipt() : payment.getOrderId();
        return "connecthub-receipt-" + suffix + ".pdf";
    }

    private String buildEmailBody(Payment payment) {
        String customerName = StringUtils.hasText(payment.getCustomerName()) ? payment.getCustomerName() : "Customer";
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "<style>" +
               "body { font-family: 'Inter', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f0f4f8; margin: 0; padding: 0; }" +
               ".container { max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.05); }" +
               ".header { background: linear-gradient(135deg, #10B981, #059669); color: #ffffff; padding: 40px 20px; text-align: center; }" +
               ".header h1 { margin: 0; font-size: 28px; font-weight: 700; letter-spacing: -0.5px; }" +
               ".content { padding: 40px 30px; color: #4B5563; line-height: 1.6; }" +
               ".content h2 { color: #1F2937; margin-top: 0; font-size: 22px; }" +
               ".content p { margin: 0 0 20px 0; font-size: 16px; }" +
               ".receipt-box { background-color: #F3F4F6; border: 1px solid #E5E7EB; border-radius: 8px; padding: 24px; margin: 24px 0; }" +
               ".receipt-item { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #E5E7EB; }" +
               ".receipt-item:last-child { border-bottom: none; }" +
               ".item-label { font-weight: 600; color: #374151; }" +
               ".item-value { color: #6B7280; text-align: right; }" +
               ".total-row { font-size: 18px; font-weight: 700; color: #10B981; margin-top: 12px; padding-top: 12px; border-top: 2px solid #E5E7EB; }" +
               ".footer { background-color: #F9FAFB; padding: 24px; text-align: center; color: #9CA3AF; font-size: 14px; border-top: 1px solid #E5E7EB; }" +
               ".footer p { margin: 0; }" +
               "</style>" +
               "</head>" +
               "<body>" +
               "<div class=\"container\">" +
               "<div class=\"header\">" +
               "<h1>Payment Successful</h1>" +
               "</div>" +
               "<div class=\"content\">" +
               "<h2>Hello " + customerName + ",</h2>" +
               "<p>Thank you for your purchase! Your ConnectHub payment was successful and your credits have been added to your account.</p>" +
               "<div class=\"receipt-box\">" +
               "<div class=\"receipt-item\"><span class=\"item-label\">Receipt No</span><span class=\"item-value\">" + payment.getReceipt() + "</span></div>" +
               "<div class=\"receipt-item\"><span class=\"item-label\">Order ID</span><span class=\"item-value\">" + payment.getOrderId() + "</span></div>" +
               "<div class=\"receipt-item\"><span class=\"item-label\">Payment ID</span><span class=\"item-value\">" + payment.getPaymentId() + "</span></div>" +
               "<div class=\"receipt-item\"><span class=\"item-label\">Credits Added</span><span class=\"item-value\">" + payment.getCredits() + "</span></div>" +
               "<div class=\"receipt-item total-row\"><span class=\"item-label\">Amount Paid</span><span class=\"item-value\">" + payment.getCurrency() + " " + payment.getAmount() + "</span></div>" +
               "</div>" +
               "<p>We have attached a detailed PDF receipt to this email for your records.</p>" +
               "</div>" +
               "<div class=\"footer\">" +
               "<p>If you have any questions, please contact our support team.</p>" +
               "<p style=\"margin-top: 8px;\">&copy; 2026 ConnectHub. All rights reserved.</p>" +
               "</div>" +
               "</div>" +
               "</body>" +
               "</html>";
    }
}
