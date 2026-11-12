package br.com.classholder.classholder.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.dto.ReservationRequest;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class ReservationConcurrencyIntegrationTest {

    private static final int ATTEMPTS = 8;
    private static final LocalDate NEXT_TUESDAY = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private UserRepository userRepository;

    private final List<Room> createdRooms = new ArrayList<>();
    private final List<User> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Room room : createdRooms) {
            reservationRepository.deleteAll(reservationRepository.findAll().stream()
                    .filter(reservation -> reservation.getRoomId().equals(room.getId()))
                    .toList());
        }
        roomRepository.deleteAll(createdRooms);
        userRepository.deleteAll(createdUsers);
    }

    @Test
    void simultaneousReservationsOfTheSameCommonRoomSlotShouldConfirmOnlyOne() throws Exception {
        Room room = createRoom(false);
        User professor = createUser(UserRole.PROFESSOR);

        int succeeded = runConcurrently(() -> reservationService.reserveCommonRoom(professor.getId(),
                request(room)));

        assertThat(succeeded).isEqualTo(1);
        assertThat(reservationsOf(room, ReservationStatus.CONFIRMADA)).hasSize(1);
    }

    @Test
    void simultaneousRequestsFromTheSameProfessorShouldKeepOnlyOnePending() throws Exception {
        Room room = createRoom(true);
        User professor = createUser(UserRole.PROFESSOR);

        int succeeded = runConcurrently(() -> reservationService.requestSpecialRoom(professor.getId(),
                request(room)));

        assertThat(succeeded).isEqualTo(1);
        assertThat(reservationsOf(room, ReservationStatus.PENDENTE)).hasSize(1);
    }

    private int runConcurrently(Callable<?> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    action.call();
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                succeeded++;
            }
        }
        return succeeded;
    }

    private List<Reservation> reservationsOf(Room room, ReservationStatus status) {
        return reservationRepository.findAllByRoomIdAndDateAndStatus(room.getId(), NEXT_TUESDAY, status);
    }

    private ReservationRequest request(Room room) {
        return new ReservationRequest(room.getId(), NEXT_TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 0),
                "Disputa pelo mesmo horário");
    }

    private Room createRoom(boolean special) {
        Room room = roomRepository.save(Room.builder()
                .roomName("Sala Concorrencia " + System.nanoTime())
                .roomType("Sala")
                .localization("Predio C")
                .roomCapacity(30)
                .description("Sala de teste de concorrência")
                .active(true)
                .salaEspecial(special)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build());
        createdRooms.add(room);
        return room;
    }

    private User createUser(UserRole role) {
        long suffix = System.nanoTime();
        User user = userRepository.save(User.builder()
                .name(role.name() + " Concorrencia " + suffix)
                .email(role.name().toLowerCase() + ".concorrencia." + suffix + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
        createdUsers.add(user);
        return user;
    }

}
