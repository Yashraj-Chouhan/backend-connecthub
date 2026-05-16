package com.connecthub.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
/**
 * Sends transactional emails such as signup OTPs and password reset codes.
 */
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Async("mailTaskExecutor")
    public void sendOtpEmail(String toEmail, String otp) {
        if (fromEmail == null || fromEmail.trim().isEmpty()) {
            log.warn("Mock Email Delivery - OTP for {}: {}", toEmail, otp);
            log.warn("Please configure SMTP details in application.yaml to enable actual email delivery.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("ConnectHub - Password Reset OTP");
            
            String htmlContent = buildOtpEmailHtml("Password Reset", "You recently requested to reset your password for your ConnectHub account. Use the OTP below to complete the process.", otp, "This OTP is valid for 30 minutes. If you did not make this request, please ignore this email.");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("OTP email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            log.warn("Fallback - OTP for {}: {}", toEmail, otp);
        }
    }

    @Async("mailTaskExecutor")
    public void sendSignupOtpEmail(String toEmail, String username, String otp) {
        if (fromEmail == null || fromEmail.trim().isEmpty()) {
            log.warn("Mock Signup OTP Delivery - OTP for {}: {}", toEmail, otp);
            log.warn("Please configure SMTP details in application.yaml to enable actual email delivery.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("ConnectHub - Verify Your Email to Complete Registration");
            
            String htmlContent = buildOtpEmailHtml("Welcome to ConnectHub!", "Hello " + username + ",<br><br>Welcome to ConnectHub &mdash; your global connection platform! To complete your registration, please verify your email address using the code below.", otp, "This code is valid for 10 minutes. Please do not share it with anyone. If you did not create an account, please ignore this email.");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Signup OTP email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send signup OTP email to {}: {}", toEmail, e.getMessage());
            log.warn("Fallback - Signup OTP for {}: {}", toEmail, otp);
        }
    }

    private String buildOtpEmailHtml(String title, String message, String otp, String footerNote) {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "<style>" +
               "body { font-family: 'Inter', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f0f4f8; margin: 0; padding: 0; }" +
               ".container { max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.05); }" +
               ".header { background: linear-gradient(135deg, #4F46E5, #7C3AED); color: #ffffff; padding: 40px 20px; text-align: center; }" +
               ".header h1 { margin: 0; font-size: 28px; font-weight: 700; letter-spacing: -0.5px; }" +
               ".content { padding: 40px 30px; color: #4B5563; line-height: 1.6; }" +
               ".content h2 { color: #1F2937; margin-top: 0; font-size: 22px; }" +
               ".content p { margin: 0 0 20px 0; font-size: 16px; }" +
               ".otp-box { background-color: #F3F4F6; border: 2px dashed #818CF8; border-radius: 8px; padding: 24px; text-align: center; margin: 32px 0; }" +
               ".otp-code { font-size: 36px; font-weight: 800; color: #4F46E5; letter-spacing: 8px; margin: 0; }" +
               ".footer { background-color: #F9FAFB; padding: 24px; text-align: center; color: #9CA3AF; font-size: 14px; border-top: 1px solid #E5E7EB; }" +
               ".footer p { margin: 0; }" +
               "</style>" +
               "</head>" +
               "<body>" +
               "<div class=\"container\">" +
               "<div class=\"header\">" +
               "<h1>ConnectHub</h1>" +
               "</div>" +
               "<div class=\"content\">" +
               "<h2>" + title + "</h2>" +
               "<p>" + message + "</p>" +
               "<div class=\"otp-box\">" +
               "<p class=\"otp-code\">" + otp + "</p>" +
               "</div>" +
               "<p>" + footerNote + "</p>" +
               "</div>" +
               "<div class=\"footer\">" +
               "<p>&copy; 2026 ConnectHub. All rights reserved.</p>" +
               "</div>" +
               "</div>" +
               "</body>" +
               "</html>";
    }
}
