package cl.chessquery.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación mock del servicio de email.
 * En producción, reemplazar por un cliente SMTP real (SES, SendGrid, etc.).
 */
@Service
@Slf4j
public class MockEmailService {

    public void sendEmail(Long recipientId, String to, String subject, String body) {
        log.info("[MOCK EMAIL] TO={} SUBJECT='{}' BODY_PREVIEW='{}'",
                to, subject, body.substring(0, Math.min(body.length(), 100)));
    }
}
