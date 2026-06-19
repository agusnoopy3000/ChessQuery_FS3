package cl.chessquery.notifications.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    /**
     * Envía un correo HTML (con texto plano como fallback para clientes que no
     * renderizan HTML). Mismo comportamiento defensivo que {@link #sendEmail}:
     * sin JavaMailSender o ante fallo del SMTP, cae a log sin romper el consumer.
     */
    public void sendHtmlEmail(Long recipientId, String to, String subject, String text, String html) {
        if (mailSender == null) {
            log.info("[NO MAIL SENDER] TO={} SUBJECT='{}' BODY_PREVIEW='{}'",
                    to, subject, preview(text));
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);   // (plano, html) → multipart/alternative
            mailSender.send(mime);
            log.info("[MAIL SENT/HTML] TO={} SUBJECT='{}'", to, subject);
        } catch (Exception e) {
            log.warn("[MAIL FAILED] TO={} SUBJECT='{}' err={} (cayendo a log)",
                    to, subject, e.getMessage());
            log.info("[FALLBACK EMAIL] TO={} SUBJECT='{}' BODY_PREVIEW='{}'",
                    to, subject, preview(text));
        }
    }

    private static String preview(String body) {
        if (body == null) return "";
        return body.substring(0, Math.min(body.length(), 100));
    }
}
