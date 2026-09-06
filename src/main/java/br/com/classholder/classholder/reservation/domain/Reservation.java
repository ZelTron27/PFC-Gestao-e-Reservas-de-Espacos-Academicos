package br.com.classholder.classholder.reservation.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sala reservada ou solicitada.
    @NotNull(message = "Sala é obrigatória")
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @NotNull(message = "Data é obrigatória")
    @Column(nullable = false)
    private LocalDate date;

    @NotNull(message = "Horário de início é obrigatório")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull(message = "Horário de término é obrigatório")
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @NotBlank(message = "Finalidade é obrigatória")
    @Column(nullable = false, length = 500)
    private String purpose;
}
