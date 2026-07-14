package com.wellhouse.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 이메일 발송. SMTP 미설정(wellhouse.mail.enabled=false)이면 실제 발송 대신 로그로 남긴다.
 * (FCM 과 동일한 패턴: 자격이 없으면 skip 하고 개발은 로그로 확인)
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${wellhouse.mail.enabled:false}") boolean enabled,
                       @Value("${wellhouse.mail.from:no-reply@wellhouse.app}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
    }

    /**
     * 단순 텍스트 메일 전송. 미설정/실패 시 예외를 던지지 않고 로그만 남긴다.
     * @return 실제 발송에 성공했으면 true, skip/실패면 false.
     */
    public boolean send(String to, String subject, String body) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!enabled || sender == null) {
            log.warn("[MAIL] 발송 비활성/미설정 — skip. to={}, subject={}\n{}", to, subject, body);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            log.info("[MAIL] 발송 완료. to={}, subject={}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("[MAIL] 발송 실패. to={}: {}", to, e.getMessage(), e);
            return false;
        }
    }
}
