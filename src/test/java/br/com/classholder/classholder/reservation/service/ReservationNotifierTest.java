package br.com.classholder.classholder.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.service.EmailService;
import br.com.classholder.classholder.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class ReservationNotifierTest {

    private static final String EMAIL = "luis@escola.test";

    @Mock
    private EmailService emailService;
    @Mock
    private UserService userService;
    @Mock
    private RoomService roomService;

    @InjectMocks
    private ReservationNotifier notifier;

    private static Reservation reservation(ReservationStatus status) {
        return Reservation.builder()
                .id(1L)
                .roomId(3L)
                .responsibleId(5L)
                .date(LocalDate.of(2026, 3, 20))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 30))
                .status(status)
                .build();
    }

    private void responsible(String name) {
        when(userService.getUserById(5L)).thenReturn(new UserResponse(5L, name, EMAIL, UserRole.PROFESSOR));
        when(roomService.findRoomName(3L)).thenReturn("Lab 1");
    }

    private String sentBody(String expectedSubject) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq(EMAIL), eq(expectedSubject), body.capture());
        return body.getValue();
    }

    @Test
    void shouldSendApprovalEmailWithReservationDetails() {
        responsible("Luis");

        notifier.notifyStatusChange(reservation(ReservationStatus.CONFIRMADA));

        String body = sentBody("Class Holder - Solicitação aprovada");
        assertThat(body)
                .contains("<p>Olá, Luis.</p>")
                .contains("Sua solicitação de sala especial foi aprovada.")
                .contains("<li>Sala: Lab 1</li>")
                .contains("<li>Data: 20/03/2026</li>")
                .contains("<li>Horário: 08:00 &agrave;s 09:30</li>")
                .contains("<li>Status: Confirmada</li>")
                .doesNotContain("Motivo")
                .doesNotContain("Justificativa");
    }

    @ParameterizedTest
    @CsvSource({
            "RECUSADA, Solicitação recusada, Recusada",
            "EXPIRADA, Solicitação expirada, Expirada",
            "CANCELADA, Reserva ou solicitação cancelada, Cancelada",
            "PENDENTE, Solicitação pendente, Aguardando avalia&ccedil;&atilde;o"
    })
    void shouldUseSubjectAndStatusMatchingTheNewStatus(ReservationStatus status, String subject, String label) {
        responsible("Luis");

        notifier.notifyStatusChange(reservation(status));

        assertThat(sentBody("Class Holder - " + subject)).contains("<li>Status: " + label + "</li>");
    }

    @Test
    void shouldIncludeCancellationReason() {
        responsible("Luis");
        Reservation reservation = reservation(ReservationStatus.CANCELADA);
        reservation.setCancellationReason(CancellationReason.PELA_GESTAO);

        notifier.notifyStatusChange(reservation);

        assertThat(sentBody("Class Holder - Reserva ou solicitação cancelada"))
                .contains("<li>Motivo: Cancelada pela gest&atilde;o</li>");
    }

    @Test
    void shouldEscapeRejectionJustificationAndName() {
        responsible("Luis <b>");
        Reservation reservation = reservation(ReservationStatus.RECUSADA);
        reservation.setRejectionJustification("<script>alert(1)</script>");

        notifier.notifyStatusChange(reservation);

        assertThat(sentBody("Class Holder - Solicitação recusada"))
                .contains("Olá, Luis &lt;b&gt;.")
                .contains("<li>Justificativa: &lt;script&gt;alert(1)&lt;/script&gt;</li>")
                .doesNotContain("<script>");
    }

    @ParameterizedTest
    @EnumSource(value = CancellationReason.class, names = { "USUARIO_DESATIVADO", "USUARIO_EXCLUIDO" })
    void shouldSkipReasonsAlreadyReportedInTheAccountEmail(CancellationReason reason) {
        Reservation reservation = reservation(ReservationStatus.CANCELADA);
        reservation.setCancellationReason(reason);

        notifier.notifyStatusChange(reservation);

        verifyNoInteractions(emailService, userService, roomService);
    }

    @Test
    void shouldNotPropagateEmailFailure() {
        responsible("Luis");
        doThrow(new EmailService.EmailDeliveryException("falha", null))
                .when(emailService).send(anyString(), anyString(), anyString());

        assertThatCode(() -> notifier.notifyStatusChange(reservation(ReservationStatus.CONFIRMADA)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldWaitForCommitWhenInsideTransaction() {
        responsible("Luis");
        TransactionSynchronizationManager.initSynchronization();
        try {
            notifier.notifyStatusChange(reservation(ReservationStatus.CONFIRMADA));

            verify(emailService, never()).send(anyString(), anyString(), anyString());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(emailService).send(eq(EMAIL), eq("Class Holder - Solicitação aprovada"), anyString());
    }
}
