package br.com.classholder.classholder.reservation.dto;

import java.time.LocalDate;
import java.util.List;

public record PendingRequestGroup(
        Long roomId,
        String roomName,
        LocalDate date,
        List<ReservationResponse> requests) {
}
