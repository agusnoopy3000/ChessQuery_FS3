package cl.chessquery.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Servicio de email transaccional. Si hay un JavaMailSender configurado
 * (Spring lo autoconfigura cuando spring.mail.* está presente) intenta
 * enviar al SMTP definido — en local típicamente Inbucket de Supabase
 * (puerto 54325). Si el envío falla, cae a logging para no romper el
 * consumer ni perder la notificación in-app.
 */
@Service
@Slf4j
public class MockEmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MockEmailService(@Autowired(required = false) JavaMailSender mailSender,
                            @Value("${notifications.mail.from:no-reply@chessquery.cl}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendEmail(Long recipientId, String to, String subject, String body) {
        if (mailSender == null) {
            log.info("[NO MAIL SENDER] TO={} SUBJECT='{}' BODY_PREVIEW='{}'",
                    to, subject, preview(body));
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("[MAIL SENT] TO={} SUBJECT='{}'", to, subject);
        } catch (Exception e) {
            log.warn("[MAIL FAILED] TO={} SUBJECT='{}' err={} (cayendo a log)",
                    to, subject, e.getMessage());
            log.info("[FALLBACK EMAIL] TO={} SUBJECT='{}' BODY_PREVIEW='{}'",
                    to, subject, preview(body));
        }
    }

    private static String preview(String body) {
        if (body == null) return "";
        return body.substring(0, Math.min(body.length(), 100));
    }
}
