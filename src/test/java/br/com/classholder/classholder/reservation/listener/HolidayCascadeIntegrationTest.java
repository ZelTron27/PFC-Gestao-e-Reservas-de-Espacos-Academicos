package br.com.classholder.classholder.reservation.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.holiday.HolidaysRegisteredEvent;
import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HolidayCascadeIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private UserRepository userRepository;

    private Long roomId;
    private Long responsibleId;
    private LocalDate newHoliday;

    @BeforeEach
    void setUp() {
        responsibleId = userRepository.save(User.builder()
                .name("Professor Feriado")
                .email("feriado." + System.nanoTime() + "@classholder.com")
                .passwordHash("hash")
                .role(UserRole.PROFESSOR)
                .build()).getId();
        roomId = roomRepository.save(Room.builder()
                .roomName("Sala Feriado " + System.nanoTime())
                .roomType("Sala comum")
                .localization("Predio A")
                .roomCapacity(30)
                .description("Sala de teste")
                .active(true)
                .salaEspecial(false)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build()).getId();
        newHoliday = LocalDate.now().plusDays(10);
    }

    @Test
    void cancelsConfirmedReservationsAndPendingRequestsOnANewHoliday() {
        Reservation confirmed = save(newHoliday, ReservationStatus.CONFIRMADA);
        Reservation pending = save(newHoliday, ReservationStatus.PENDENTE);

        eventPublisher.publishEvent(new HolidaysRegisteredEvent(List.of(newHoliday)));

        for (Reservation reservation : List.of(confirmed, pending)) {
            Reservation reloaded = reservationRepository.findById(reservation.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
            assertThat(reloaded.getCancellationReason()).isEqualTo(CancellationReason.DIA_BLOQUEADO);
        }
    }

    @Test
    void keepsReservationsOnOtherDates() {
        Reservation otherDay = save(newHoliday.plusDays(1), ReservationStatus.CONFIRMADA);

        eventPublisher.publishEvent(new HolidaysRegisteredEvent(List.of(newHoliday)));

        assertThat(reservationRepository.findById(otherDay.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMADA);
    }

    @Test
    void keepsReservationsAlreadyCancelled() {
        Reservation cancelled = save(newHoliday, ReservationStatus.CANCELADA);
        cancelled.setCancellationReason(CancellationReason.PELO_RESPONSAVEL);
        reservationRepository.save(cancelled);

        eventPublisher.publishEvent(new HolidaysRegisteredEvent(List.of(newHoliday)));

        assertThat(reservationRepository.findById(cancelled.getId()).orElseThrow().getCancellationReason())
                .isEqualTo(CancellationReason.PELO_RESPONSAVEL);
    }

    private Reservation save(LocalDate date, ReservationStatus status) {
        return reservationRepository.save(Reservation.builder()
                .roomId(roomId)
                .responsibleId(responsibleId)
                .date(date)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .purpose("Aula")
                .status(status)
                .build());
    }

}
