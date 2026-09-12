package br.com.classholder.classholder.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import br.com.classholder.classholder.reservation.ReservationStatus;

public record ReservationResponse(
        Long id,
        Long roomId,
        String roomName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String purpose,
        String professorName,
        ReservationStatus status) {
}
