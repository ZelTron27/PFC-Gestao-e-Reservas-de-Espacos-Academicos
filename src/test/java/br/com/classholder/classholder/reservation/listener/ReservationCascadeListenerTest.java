package br.com.classholder.classholder.reservation.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.classholder.classholder.holiday.HolidaysRegisteredEvent;
import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.service.ReservationService;
import br.com.classholder.classholder.room.RoomDeletionRequestedEvent;
import br.com.classholder.classholder.room.RoomInactivatedEvent;
import br.com.classholder.classholder.user.UserAnonymizedEvent;
import br.com.classholder.classholder.user.UserDeactivatedEvent;

@ExtendWith(MockitoExtension.class)
class ReservationCascadeListenerTest {

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationCascadeListener listener;

    @Test
    void userDeactivatedShouldCancelUpcomingReservationsAndCollectDescriptions() {
        when(reservationService.listUpcomingReservationIdsByResponsible(5L)).thenReturn(List.of(1L, 2L, 3L));
        when(reservationService.cancelForCascade(1L, CancellationReason.USUARIO_DESATIVADO))
                .thenReturn(Optional.of("Lab 1 - 10/03/2026"));
        when(reservationService.cancelForCascade(2L, CancellationReason.USUARIO_DESATIVADO))
                .thenReturn(Optional.empty());
        when(reservationService.cancelForCascade(3L, CancellationReason.USUARIO_DESATIVADO))
                .thenReturn(Optional.of("Lab 2 - 11/03/2026"));
        UserDeactivatedEvent event = new UserDeactivatedEvent(5L);

        listener.onUserDeactivated(event);

        assertThat(event.cancelledReservations()).containsExactly("Lab 1 - 10/03/2026", "Lab 2 - 11/03/2026");
    }

    @Test
    void userDeactivatedWithoutReservationsShouldCancelNothing() {
        when(reservationService.listUpcomingReservationIdsByResponsible(5L)).thenReturn(List.of());
        UserDeactivatedEvent event = new UserDeactivatedEvent(5L);

        listener.onUserDeactivated(event);

        assertThat(event.cancelledReservations()).isEmpty();
        verify(reservationService, never()).cancelForCascade(anyLong(), any());
    }

    @Test
    void userAnonymizedShouldCancelWithExcludedReason() {
        when(reservationService.listUpcomingReservationIdsByResponsible(5L)).thenReturn(List.of(1L));
        when(reservationService.cancelForCascade(1L, CancellationReason.USUARIO_EXCLUIDO))
                .thenReturn(Optional.of("Lab 1 - 10/03/2026"));
        UserAnonymizedEvent event = new UserAnonymizedEvent(5L);

        listener.onUserAnonymized(event);

        assertThat(event.cancelledReservations()).containsExactly("Lab 1 - 10/03/2026");
    }

    @Test
    void roomInactivatedShouldCancelUpcomingReservationsOfTheRoom() {
        when(reservationService.listUpcomingReservationIdsByRoom(3L)).thenReturn(List.of(7L, 8L));

        listener.onRoomInactivated(new RoomInactivatedEvent(3L));

        verify(reservationService).cancelForCascade(7L, CancellationReason.SALA_INATIVADA);
        verify(reservationService).cancelForCascade(8L, CancellationReason.SALA_INATIVADA);
    }

    @Test
    void holidaysRegisteredShouldCancelReservationsOnEachDate() {
        LocalDate carnival = LocalDate.of(2026, 2, 17);
        LocalDate tiradentes = LocalDate.of(2026, 4, 21);
        when(reservationService.listUpcomingReservationIdsByDate(carnival)).thenReturn(List.of(1L));
        when(reservationService.listUpcomingReservationIdsByDate(tiradentes)).thenReturn(List.of(2L, 3L));

        listener.onHolidaysRegistered(new HolidaysRegisteredEvent(List.of(carnival, tiradentes)));

        verify(reservationService).cancelForCascade(1L, CancellationReason.DIA_BLOQUEADO);
        verify(reservationService).cancelForCascade(2L, CancellationReason.DIA_BLOQUEADO);
        verify(reservationService).cancelForCascade(3L, CancellationReason.DIA_BLOQUEADO);
    }

    @Test
    void holidaysRegisteredWithoutDatesShouldDoNothing() {
        listener.onHolidaysRegistered(new HolidaysRegisteredEvent(List.of()));

        verify(reservationService, never()).listUpcomingReservationIdsByDate(any());
    }

    @Test
    void roomDeletionShouldBeBlockedWhenThereAreOpenReservations() {
        when(reservationService.hasOpenReservations(3L)).thenReturn(true);

        assertThatThrownBy(() -> listener.onRoomDeletionRequested(new RoomDeletionRequestedEvent(3L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Essa sala ainda tem reservas ou solicitações em aberto");
    }

    @Test
    void roomDeletionShouldBeAllowedWhenThereAreNoOpenReservations() {
        when(reservationService.hasOpenReservations(3L)).thenReturn(false);

        assertThatCode(() -> listener.onRoomDeletionRequested(new RoomDeletionRequestedEvent(3L)))
                .doesNotThrowAnyException();
    }
}
