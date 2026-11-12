package br.com.classholder.classholder.reservation.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;

@DataJpaTest
@ActiveProfiles("test")
class ReservationRepositoryIntegrationTest {

    private static final Long ROOM_ID = 1L;
    private static final LocalDate DAY = LocalDate.of(2030, 3, 4);

    @Autowired
    private ReservationRepository reservationRepository;

    private Reservation saved;

    @BeforeEach
    void setUp() {
        saved = reservationRepository.save(Reservation.builder()
                .roomId(ROOM_ID)
                .responsibleId(7L)
                .date(DAY)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .purpose("Aula")
                .build());
    }

    @Test
    void shouldFindConflictWhenIntervalsOverlap() {
        List<Reservation> conflicts = reservationRepository.findConflicting(ROOM_ID, DAY, LocalTime.of(10, 30),
                LocalTime.of(11, 30), ReservationStatus.CONFIRMADA);

        assertEquals(1, conflicts.size());
        assertEquals(saved.getId(), conflicts.get(0).getId());
    }

    @Test
    void shouldNotFindConflictWhenNewReservationStartsExactlyWhenExistingEnds() {
        List<Reservation> conflicts = reservationRepository.findConflicting(ROOM_ID, DAY, LocalTime.of(11, 0),
                LocalTime.of(12, 0), ReservationStatus.CONFIRMADA);

        assertTrue(conflicts.isEmpty());
    }

}
