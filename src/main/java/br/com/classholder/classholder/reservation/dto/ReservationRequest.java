package br.com.classholder.classholder.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(

        @NotNull(message = "Sala é obrigatória")
        Long roomId,

        @NotNull(message = "Data é obrigatória")
        LocalDate date,

        @NotNull(message = "Horário de início é obrigatório")
        LocalTime startTime,

        @NotNull(message = "Horário de término é obrigatório")
        LocalTime endTime,

        @NotBlank(message = "Finalidade é obrigatória")
        String purpose) {
}
