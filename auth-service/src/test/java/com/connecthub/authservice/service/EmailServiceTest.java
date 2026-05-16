package com.connecthub.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
    }

    @Test
    void sendOtpEmail_WithFromEmail_SendsEmail() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "from@example.com");
        String toEmail = "to@example.com";
        String otp = "123456";
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOtpEmail(toEmail, otp);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendOtpEmail_WithoutFromEmail_LogsOtp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "");
        String toEmail = "to@example.com";
        String otp = "123456";

        emailService.sendOtpEmail(toEmail, otp);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendOtpEmail_SendFails_LogsFallback() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "from@example.com");
        String toEmail = "to@example.com";
        String otp = "123456";
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Send failed")).when(mailSender).send(mimeMessage);

        emailService.sendOtpEmail(toEmail, otp);

        verify(mailSender).send(mimeMessage);
    }
}
