package br.com.classholder.classholder.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String baseUrl;

    public EmailService(JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.baseUrl = baseUrl;
    }

    public void sendPasswordResetEmail(String toEmail, String token, long ttlMinutes) {
        String resetLink = baseUrl + "/senha/redefinir?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Class Holder - Redefinição de senha");
        message.setText("""
                Recebemos uma solicitação para redefinir a senha da sua conta no Class Holder.

                Para escolher uma nova senha, acesse o link abaixo:
                %s

                Este link expira em %d minutos e só pode ser usado uma vez.

                Se você não solicitou essa alteração, ignore este e-mail e sua senha atual continuará válida.
                """.formatted(resetLink, ttlMinutes));

        mailSender.send(message);
    }

}
