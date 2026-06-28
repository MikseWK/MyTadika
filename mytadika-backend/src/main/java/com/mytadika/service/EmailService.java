package com.mytadika.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = baseUrl + "/pages/reset-password.html?token=" + token;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("MyTadika — Reset Your Password");
            helper.setText(
                "<html><body style='font-family:sans-serif;color:#3D3D3D;max-width:480px;margin:0 auto;'>" +
                "<h2 style='color:#B8860B;'>MyTadika</h2>" +
                "<p>Hi there!</p>" +
                "<p>We received a request to reset your MyTadika password.</p>" +
                "<p>Open this link in your browser to set a new password:</p>" +
                "<p style='background:#FFF8E8;border:1px solid #E8E2D9;border-radius:8px;padding:12px;" +
                "word-break:break-all;font-size:13px;'>" + resetLink + "</p>" +
                "<p>Or go to <strong>MyTadika → Forgot Password → Reset Password</strong> and enter this code:</p>" +
                "<p style='text-align:center;'>" +
                "<span style='display:inline-block;background:#FFC727;border-radius:8px;padding:12px 24px;" +
                "font-size:20px;font-weight:bold;letter-spacing:2px;color:#3D3D3D;'>" + token + "</span>" +
                "</p>" +
                "<p style='color:#857668;font-size:12px;'>This code expires in 1 hour. " +
                "If you didn't request this, you can safely ignore this email.</p>" +
                "<p>— The MyTadika Team</p>" +
                "</body></html>",
                true
            );
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}
