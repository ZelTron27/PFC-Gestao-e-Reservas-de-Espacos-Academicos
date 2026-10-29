package br.com.classholder.classholder.reservation.controller;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    @Min(value = 1, message = "A reserva deve ter no mínimo 30 minutos")
    @Max(value = 6, message = "A reserva deve ter no máximo 3 horas")
    private Integer durationBlocks = 1;

    @NotBlank(message = "Finalidade é obrigatória")
    private String purpose;

}
