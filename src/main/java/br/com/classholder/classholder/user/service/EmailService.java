package br.com.classholder.classholder.user.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Resend resend;
    private final String fromAddress;
    private final String baseUrl;
    private final boolean enabled;
    private final long minIntervalMillis;
    private long nextSendAtMillis;

    public EmailService(@Value("${app.mail.resend-api-key}") String resendApiKey,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.mail.enabled:true}") boolean enabled,
            @Value("${app.mail.min-interval-ms:600}") long minIntervalMillis) {
        this.resend = new Resend(resendApiKey);
        this.fromAddress = fromAddress;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.minIntervalMillis = minIntervalMillis;
    }

    public void sendPasswordResetEmail(String toEmail, String token, long ttlMinutes) {
        String resetLink = HtmlUtils.htmlEscape(baseUrl + "/senha/redefinir?token=" + token);

        send(toEmail, "Class Holder - Redefinição de senha", """
                <p>Recebemos uma solicitação para redefinir a senha da sua conta no Class Holder.</p>
                <p>Para escolher uma nova senha, acesse o link abaixo:</p>
                <p><a href="%s">%s</a></p>
                <p>Este link expira em %d minutos e só pode ser usado uma vez.</p>
                <p>Se você não solicitou essa alteração, ignore este e-mail e sua senha atual continuará válida.</p>
                """.formatted(resetLink, resetLink, ttlMinutes));
    }

    public void sendAccountDeactivatedEmail(String toEmail, String name, List<String> cancelledReservations) {
        StringBuilder reservations = new StringBuilder();
        if (!cancelledReservations.isEmpty()) {
            reservations.append("<p>As reservas e solicitações abaixo foram canceladas:</p><ul>");
            for (String reservation : cancelledReservations) {
                reservations.append("<li>").append(HtmlUtils.htmlEscape(reservation)).append("</li>");
            }
            reservations.append("</ul>");
        }

        send(toEmail, "Class Holder - Conta desativada", """
                <p>Olá, %s.</p>
                <p>Sua conta no Class Holder foi desativada e você não poderá mais acessar o sistema.</p>
                %s
                <p>Em caso de dúvidas, procure a coordenação.</p>
                """.formatted(HtmlUtils.htmlEscape(name), reservations));
    }

    public void send(String toEmail, String subject, String html) {
        if (!enabled) {
            log.debug("Envio de e-mail desabilitado, mensagem \"{}\" não enviada", subject);
            return;
        }

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(toEmail)
                .subject(subject)
                .html(html)
                .build();

        try {
            waitForSendInterval();
            resend.emails().send(params);
        } catch (ResendException e) {
            throw new EmailDeliveryException("Falha ao enviar e-mail via Resend", e);
        }
    }

    private synchronized void waitForSendInterval() {
        long waitMillis = nextSendAtMillis - System.currentTimeMillis();
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        nextSendAtMillis = System.currentTimeMillis() + minIntervalMillis;
    }

    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
