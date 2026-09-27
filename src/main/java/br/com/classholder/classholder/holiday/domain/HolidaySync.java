package br.com.classholder.classholder.holiday.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sincronizacoes_feriados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidaySync {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executada_em", nullable = false)
    private LocalDateTime executedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "gatilho", nullable = false, length = 20)
    private HolidaySyncTrigger trigger;

    @Column(name = "sucesso", nullable = false)
    private boolean success;

    @Column(name = "fonte", nullable = false, length = 50)
    private String source;

    @Column(name = "anos", nullable = false, length = 50)
    private String years;

    @Column(name = "total_feriados")
    private Integer totalHolidays;

    @Column(name = "mensagem", columnDefinition = "TEXT")
    private String message;

}
