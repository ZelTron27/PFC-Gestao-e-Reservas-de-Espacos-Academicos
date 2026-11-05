package br.com.classholder.classholder.reservation.service;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.shared.WhenChangesSaved;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.service.EmailService;
import br.com.classholder.classholder.user.service.UserService;

@Component
public class ReservationNotifier {

    private static final Logger log = LoggerFactory.getLogger(ReservationNotifier.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<CancellationReason> REPORTED_IN_ACCOUNT_EMAIL = EnumSet
            .of(CancellationReason.USUARIO_DESATIVADO, CancellationReason.USUARIO_EXCLUIDO);

    private final EmailService emailService;
    private final UserService userService;
    private final RoomService roomService;

    public ReservationNotifier(EmailService emailService, UserService userService, RoomService roomService) {
        this.emailService = emailService;
        this.userService = userService;
        this.roomService = roomService;
    }

    public void notifyStatusChange(Reservation reservation) {
        if (reservation.getCancellationReason() != null
                && REPORTED_IN_ACCOUNT_EMAIL.contains(reservation.getCancellationReason())) {
            return;
        }

        UserResponse responsible = userService.getUserById(reservation.getResponsibleId());
        String subject = "Class Holder - " + subjectOf(reservation.getStatus());
        String html = buildBody(reservation, responsible.name());

        WhenChangesSaved.run(() -> deliver(responsible, subject, html));
    }

    private void deliver(UserResponse responsible, String subject, String html) {
        try {
            emailService.send(responsible.email(), subject, html);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de mudança de status para o usuário {}", responsible.id(), e);
        }
    }

    private String buildBody(Reservation reservation, String responsibleName) {
        StringBuilder details = new StringBuilder();
        appendItem(details, "Sala", roomService.findRoomName(reservation.getRoomId()));
        appendItem(details, "Data", reservation.getDate().format(DATE_FORMAT));
        appendItem(details, "Horário", reservation.getStartTime() + " às " + reservation.getEndTime());
        appendItem(details, "Status", statusOf(reservation.getStatus()));
        if (reservation.getCancellationReason() != null) {
            appendItem(details, "Motivo", reservation.getCancellationReason().label());
        }
        if (reservation.getRejectionJustification() != null) {
            appendItem(details, "Justificativa", reservation.getRejectionJustification());
        }

        return """
                <p>Olá, %s.</p>
                <p>%s</p>
                <ul>%s</ul>
                """.formatted(HtmlUtils.htmlEscape(responsibleName), introOf(reservation.getStatus()), details);
    }

    private void appendItem(StringBuilder details, String label, String value) {
        details.append("<li>").append(label).append(": ").append(HtmlUtils.htmlEscape(value)).append("</li>");
    }

    private String subjectOf(ReservationStatus status) {
        return switch (status) {
            case CONFIRMADA -> "Solicitação aprovada";
            case RECUSADA -> "Solicitação recusada";
            case EXPIRADA -> "Solicitação expirada";
            case CANCELADA -> "Reserva ou solicitação cancelada";
            case PENDENTE -> "Solicitação pendente";
        };
    }

    private String statusOf(ReservationStatus status) {
        return switch (status) {
            case CONFIRMADA -> "Confirmada";
            case RECUSADA -> "Recusada";
            case EXPIRADA -> "Expirada";
            case CANCELADA -> "Cancelada";
            case PENDENTE -> "Aguardando avaliação";
        };
    }

    private String introOf(ReservationStatus status) {
        return switch (status) {
            case CONFIRMADA -> "Sua solicitação de sala especial foi aprovada.";
            case RECUSADA -> "Sua solicitação de sala especial foi recusada.";
            case EXPIRADA -> "Sua solicitação de sala especial expirou sem avaliação.";
            case CANCELADA -> "Sua reserva ou solicitação foi cancelada.";
            case PENDENTE -> "Sua solicitação de sala especial aguarda avaliação.";
        };
    }

}
