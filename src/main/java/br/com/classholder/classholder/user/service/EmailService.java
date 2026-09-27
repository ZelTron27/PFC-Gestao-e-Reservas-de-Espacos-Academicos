package br.com.classholder.classholder.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

@Service
public class EmailService {

    private final Resend resend;
    private final String fromAddress;
    private final String baseUrl;

    public EmailService(@Value("${app.mail.resend-api-key}") String resendApiKey,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.base-url}") String baseUrl) {
        this.resend = new Resend(resendApiKey);
        this.fromAddress = fromAddress;
        this.baseUrl = baseUrl;
    }

    public void sendPasswordResetEmail(String toEmail, String token, long ttlMinutes) {
        String resetLink = HtmlUtils.htmlEscape(baseUrl + "/senha/redefinir?token=" + token);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(toEmail)
                .subject("Class Holder - Redefinição de senha")
                .html("""
                        <p>Recebemos uma solicitação para redefinir a senha da sua conta no Class Holder.</p>
                        <p>Para escolher uma nova senha, acesse o link abaixo:</p>
                        <p><a href="%s">%s</a></p>
                        <p>Este link expira em %d minutos e só pode ser usado uma vez.</p>
                        <p>Se você não solicitou essa alteração, ignore este e-mail e sua senha atual continuará válida.</p>
                        """.formatted(resetLink, resetLink, ttlMinutes))
                .build();

        try {
            resend.emails().send(params);
        } catch (ResendException e) {
            throw new EmailDeliveryException("Falha ao enviar e-mail via Resend", e);
        }
    }

    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
