package br.com.classholder.classholder.reservation.controller;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@NoArgsConstructor
public class ReservationFormData {

    @NotNull(message = "Sala é obrigatória")
    private Long roomId;

    @NotNull(message = "Data é obrigatória")
    @FutureOrPresent(message = "A data da reserva não pode ser no passado")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Horário de início é obrigatório")
    private LocalTime startTime;

    @NotNull(message = "Duração é obrigatória")
    private Integer durationBlocks;

    @NotBlank(message = "Finalidade é obrigatória")
    private String purpose;

}
