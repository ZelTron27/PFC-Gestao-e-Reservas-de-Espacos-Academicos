package br.com.classholder.classholder.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;

class EmailServiceTest {

    private static final String FROM = "Class Holder <onboarding@resend.dev>";
    private static final String BASE_URL = "https://classholder.test";

    private Emails emails;
    private MockedConstruction<Resend> resendConstruction;

    @BeforeEach
    void setUp() {
        emails = Mockito.mock(Emails.class);
        resendConstruction = mockConstruction(Resend.class,
                (resend, context) -> when(resend.emails()).thenReturn(emails));
    }

    @AfterEach
    void tearDown() {
        resendConstruction.close();
    }

    private EmailService service(boolean enabled, long minIntervalMillis) {
        return new EmailService("re_test_key", FROM, BASE_URL, enabled, minIntervalMillis);
    }

    private CreateEmailOptions sentEmail() throws ResendException {
        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendShouldBuildMessageWithConfiguredSender() throws ResendException {
        service(true, 0).send("luis@escola.test", "Assunto", "<p>Corpo</p>");

        CreateEmailOptions email = sentEmail();
        assertThat(email.getFrom()).isEqualTo(FROM);
        assertThat(email.getTo()).containsExactly("luis@escola.test");
        assertThat(email.getSubject()).isEqualTo("Assunto");
        assertThat(email.getHtml()).isEqualTo("<p>Corpo</p>");
    }

    @Test
    void sendShouldDoNothingWhenMailIsDisabled() {
        service(false, 0).send("luis@escola.test", "Assunto", "<p>Corpo</p>");

        verifyNoInteractions(emails);
    }

    @Test
    void sendShouldWrapProviderFailure() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("limite atingido"));

        EmailService service = service(true, 0);

        assertThatThrownBy(() -> service.send("luis@escola.test", "Assunto", "<p>Corpo</p>"))
                .isInstanceOf(EmailService.EmailDeliveryException.class)
                .hasMessage("Falha ao enviar e-mail via Resend")
                .hasCauseInstanceOf(ResendException.class);
    }

    @Test
    void sendShouldWaitMinimumIntervalBetweenMessages() throws ResendException {
        EmailService service = service(true, 150);

        long start = System.nanoTime();
        service.send("a@escola.test", "1", "<p>1</p>");
        service.send("b@escola.test", "2", "<p>2</p>");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isGreaterThanOrEqualTo(140);
        verify(emails, times(2)).send(any(CreateEmailOptions.class));
    }

    @Test
    void passwordResetEmailShouldContainEscapedLinkAndExpiration() throws ResendException {
        service(true, 0).sendPasswordResetEmail("luis@escola.test", "abc&def", 30);

        CreateEmailOptions email = sentEmail();
        assertThat(email.getSubject()).isEqualTo("Class Holder - Redefinição de senha");
        assertThat(email.getHtml())
                .contains("href=\"https://classholder.test/senha/redefinir?token=abc&amp;def\"")
                .contains("expira em 30 minutos");
    }

    @Test
    void deactivationEmailShouldListCancelledReservationsEscaped() throws ResendException {
        service(true, 0).sendAccountDeactivatedEmail("luis@escola.test", "Luis <Silva>",
                List.of("Lab 1 - 10/03/2026", "Sala <B>"));

        CreateEmailOptions email = sentEmail();
        assertThat(email.getSubject()).isEqualTo("Class Holder - Conta desativada");
        assertThat(email.getHtml())
                .contains("Olá, Luis &lt;Silva&gt;.")
                .contains("As reservas e solicitações abaixo foram canceladas:")
                .contains("<li>Lab 1 - 10/03/2026</li>")
                .contains("<li>Sala &lt;B&gt;</li>");
    }

    @Test
    void deactivationEmailShouldOmitListWhenNothingWasCancelled() throws ResendException {
        service(true, 0).sendAccountDeactivatedEmail("luis@escola.test", "Luis", List.of());

        assertThat(sentEmail().getHtml())
                .doesNotContain("foram canceladas")
                .doesNotContain("<ul>")
                .contains("Sua conta no Class Holder foi desativada");
    }
}
