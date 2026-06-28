package com.mytadika.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
        String resetLink = baseUrl + "/resetpassword.html?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("MyTadika — Reset Your Password");
        message.setText(
            "Hi there!\n\n" +
            "We received a request to reset your MyTadika password.\n\n" +
            "Click the link below to set a new password (valid for 1 hour):\n" +
            resetLink + "\n\n" +
            "If you didn't request this, you can safely ignore this email.\n\n" +
            "— The MyTadika Team"
        );

        mailSender.send(message);
    }
}
