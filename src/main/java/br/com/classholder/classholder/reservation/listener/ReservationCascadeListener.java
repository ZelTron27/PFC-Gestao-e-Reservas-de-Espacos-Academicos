package br.com.classholder.classholder.reservation.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.service.ReservationService;
import br.com.classholder.classholder.room.RoomDeletionRequestedEvent;
import br.com.classholder.classholder.room.RoomInactivatedEvent;
import br.com.classholder.classholder.user.UserDeactivatedEvent;

@Component
public class ReservationCascadeListener {

    private final ReservationService reservationService;

    public ReservationCascadeListener(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @EventListener
    public void onUserDeactivated(UserDeactivatedEvent event) {
        for (Long reservationId : reservationService.listUpcomingReservationIdsByResponsible(event.userId())) {
            reservationService.cancelForCascade(reservationId, CancellationReason.USUARIO_DESATIVADO)
                    .ifPresent(event::addCancelledReservation);
        }
    }

    @EventListener
    public void onRoomInactivated(RoomInactivatedEvent event) {
        for (Long reservationId : reservationService.listUpcomingReservationIdsByRoom(event.roomId())) {
            reservationService.cancelForCascade(reservationId, CancellationReason.SALA_INATIVADA);
        }
    }

    @EventListener
    public void onRoomDeletionRequested(RoomDeletionRequestedEvent event) {
        if (reservationService.hasOpenReservations(event.roomId())) {
            throw new IllegalStateException("Essa sala ainda tem reservas ou solicitações em aberto");
        }
    }

}
