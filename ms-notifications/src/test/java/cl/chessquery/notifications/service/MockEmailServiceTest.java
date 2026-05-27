package cl.chessquery.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
}
