package cl.chessquery.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link MockEmailService}.
 *
 * <p>Verifica que sin JavaMailSender solo logea, con JavaMailSender envía
 * SimpleMailMessage, y ante fallo del SMTP no propaga excepción.</p>
 */
@ExtendWith(MockitoExtension.class)
class MockEmailServiceTest {

    @Test
    @DisplayName("sendEmail_nullSender_doesNotThrow")
    void sendEmail_nullSender_doesNotThrow() {
        MockEmailService svc = new MockEmailService(null, "no-reply@x.cl");
        assertThatNoException().isThrownBy(() ->
                svc.sendEmail(1L, "to@x.cl", "subject", "body"));
    }

    @Test
    @DisplayName("sendEmail_withSender_invokesSendOnce")
    void sendEmail_withSender_invokesSendOnce() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MockEmailService svc = new MockEmailService(sender, "no-reply@x.cl");
        svc.sendEmail(1L, "to@x.cl", "Hola", "Body");
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendEmail_senderThrows_doesNotPropagate")
    void sendEmail_senderThrows_doesNotPropagate() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        MockEmailService svc = new MockEmailService(sender, "no-reply@x.cl");
        assertThatNoException().isThrownBy(() ->
                svc.sendEmail(1L, "to@x.cl", "s", "b"));
    }

    @Test
    @DisplayName("sendEmail_longBody_truncatesPreviewWithoutCrashing")
    void sendEmail_longBody_truncatesPreviewWithoutCrashing() {
        MockEmailService svc = new MockEmailService(null, "no-reply@x.cl");
        String longBody = "x".repeat(500);
        svc.sendEmail(1L, "to@x.cl", "s", longBody);
    }

    @Test
    @DisplayName("sendEmail_nullBody_doesNotCrash")
    void sendEmail_nullBody_doesNotCrash() {
        MockEmailService svc = new MockEmailService(null, "no-reply@x.cl");
        svc.sendEmail(1L, "to@x.cl", "s", null);
    }

    // ── HTML ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendHtmlEmail_nullSender_doesNotThrow")
    void sendHtmlEmail_nullSender_doesNotThrow() {
        MockEmailService svc = new MockEmailService(null, "no-reply@x.cl");
        assertThatNoException().isThrownBy(() ->
                svc.sendHtmlEmail(1L, "to@x.cl", "Hola", "texto", "<b>html</b>"));
    }

    @Test
    @DisplayName("sendHtmlEmail_withSender_sendsMimeMessage")
    void sendHtmlEmail_withSender_sendsMimeMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);
        // createMimeMessage debe devolver un MimeMessage real para que el helper opere.
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        MockEmailService svc = new MockEmailService(sender, "no-reply@x.cl");
        svc.sendHtmlEmail(1L, "to@x.cl", "Bienvenido", "texto plano", "<h1>Hola</h1>");
        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendHtmlEmail_senderThrows_doesNotPropagate")
    void sendHtmlEmail_senderThrows_doesNotPropagate() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doThrow(new MailSendException("smtp down")).when(sender).send(any(MimeMessage.class));
        MockEmailService svc = new MockEmailService(sender, "no-reply@x.cl");
        assertThatNoException().isThrownBy(() ->
                svc.sendHtmlEmail(1L, "to@x.cl", "s", "t", "<p>h</p>"));
    }
}
