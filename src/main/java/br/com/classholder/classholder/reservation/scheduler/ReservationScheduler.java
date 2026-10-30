package br.com.classholder.classholder.reservation.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.classholder.classholder.reservation.service.ReservationService;

@Component
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);

    private final ReservationService reservationService;

    public ReservationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(cron = "${app.reservas.check-in-cron}", zone = "America/Sao_Paulo")
    public void cancelReservationsWithMissedCheckIn() {
        for (Long reservationId : reservationService.listReservationIdsWithMissedCheckIn()) {
            try {
                reservationService.cancelForMissedCheckIn(reservationId);
            } catch (RuntimeException e) {
                log.warn("Não foi possível cancelar a reserva {} por falta de check-in", reservationId, e);
            }
        }
    }

}
