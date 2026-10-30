package br.com.classholder.classholder.reservation.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;

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

    @NotNull(message = "Responsável é obrigatório")
    @Column(name = "responsavel_id", nullable = false)
    private Long responsibleId;

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

    @NotNull(message = "Status é obrigatório")
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.CONFIRMADA;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cancelamento", length = 40)
    private CancellationReason cancellationReason;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "avaliado_por")
    private Long evaluatedById;

    @Column(name = "avaliado_em")
    private Instant evaluatedAt;

    @Column(name = "justificativa_recusa", length = 500)
    private String rejectionJustification;
}
