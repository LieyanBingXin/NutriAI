package com.nutriai.service;

import com.nutriai.config.NutriAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final NutriAiProperties properties;
    private final MailProperties mailProperties;

    public void sendVerificationEmail(String to, String username, String token) {
        String subject = "【NutriAI】验证您的邮箱";
        String verifyUrl = properties.getMail().getBaseUrl() + "/api/auth/verify?token=" + token;
        String body = String.format("""
                您好，%s！
                
                感谢注册 NutriAI。请点击下方链接完成邮箱验证：
                %s
                
                链接有效期为 24 小时。如果这不是您本人操作，请忽略此邮件。
                
                — NutriAI 团队
                """, username, verifyUrl);

        sendSimple(to, subject, body);
    }

    public void sendPasswordResetEmail(String to, String username, String token) {
        String subject = "【NutriAI】重置密码";
        String resetUrl = properties.getMail().getBaseUrl() + "/reset-password?token=" + token;
        String body = String.format("""
                您好，%s！
                
                我们收到了您的密码重置请求。请点击下方链接设置新密码：
                %s
                
                链接有效期为 1 小时。如果这不是您本人操作，请忽略此邮件，您的密码不会改变。
                
                — NutriAI 团队
                """, username, resetUrl);

        sendSimple(to, subject, body);
    }

    public boolean isConfigured() {
        String host = mailProperties.getHost();
        String username = mailProperties.getUsername();
        return host != null && !host.isBlank() && username != null && !username.isBlank();
    }

    private void sendSimple(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setFrom(properties.getMail().getFrom());
        msg.setSubject(subject);
        msg.setText(body);

        try {
            mailSender.send(msg);
            log.info("邮件发送成功: to={}, subject={}", to, subject);
        } catch (MailException e) {
            log.error("邮件发送失败: {}", e.getMessage());
            throw new RuntimeException("邮件发送失败: " + e.getMessage());
        }
    }
}