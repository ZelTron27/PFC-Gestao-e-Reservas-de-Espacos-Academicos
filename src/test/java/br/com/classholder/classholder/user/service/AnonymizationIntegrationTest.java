package br.com.classholder.classholder.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.PasswordResetToken;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.PasswordResetTokenRepository;
import br.com.classholder.classholder.user.repository.UserRepository;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnonymizationIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = save(UserRole.ADMIN);
    }

    @Test
    void adminAnonymizesAProfessor() {
        User professor = save(UserRole.PROFESSOR);
        Long roomId = saveRoom();
        Reservation future = saveReservation(professor.getId(), roomId, LocalDate.now().plusDays(5));
        Reservation past = saveReservation(professor.getId(), roomId, LocalDate.now().minusDays(5));
        tokenRepository.save(PasswordResetToken.builder()
                .userId(professor.getId())
                .tokenHash("hash-" + System.nanoTime())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdAt(LocalDateTime.now())
                .build());

        userService.anonymizeUser(admin.getId(), UserRole.ADMIN, professor.getId());

        User anonymized = userRepository.findById(professor.getId()).orElseThrow();
        assertThat(anonymized.getName()).isEqualTo("Usuário excluído (ID " + professor.getId() + ")");
        assertThat(anonymized.getEmail()).isEqualTo("excluido-" + professor.getId() + "@anonimizado.invalid");
        assertThat(anonymized.getPasswordHash()).isEqualTo("!");
        assertThat(anonymized.getTwoFactorSecret()).isNull();
        assertThat(anonymized.isActive()).isFalse();
        assertThat(anonymized.isAnonymized()).isTrue();

        Reservation cancelled = reservationRepository.findById(future.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(cancelled.getCancellationReason()).isEqualTo(CancellationReason.USUARIO_EXCLUIDO);
        assertThat(reservationRepository.findById(past.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(tokenRepository.findByUserIdAndUsedAtIsNull(professor.getId())).isEmpty();
    }

    @Test
    void adminAnonymizesAnAlreadyDeactivatedUser() {
        User professor = save(UserRole.PROFESSOR);
        professor.setActive(false);
        userRepository.save(professor);

        userService.anonymizeUser(admin.getId(), UserRole.ADMIN, professor.getId());

        assertThat(userRepository.findById(professor.getId()).orElseThrow().isAnonymized()).isTrue();
    }

    @Test
    void coordinatorCannotAnonymize() {
        User coordinator = save(UserRole.COORDENACAO);
        User professor = save(UserRole.PROFESSOR);

        assertThrows(IllegalArgumentException.class,
                () -> userService.anonymizeUser(coordinator.getId(), UserRole.COORDENACAO, professor.getId()));

        assertThat(userRepository.findById(professor.getId()).orElseThrow().isAnonymized()).isFalse();
    }

    @Test
    void adminAnonymizesAnotherAdmin() {
        User otherAdmin = save(UserRole.ADMIN);

        userService.anonymizeUser(admin.getId(), UserRole.ADMIN, otherAdmin.getId());

        User anonymized = userRepository.findById(otherAdmin.getId()).orElseThrow();
        assertThat(anonymized.isAnonymized()).isTrue();
        assertThat(anonymized.isActive()).isFalse();
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void adminCannotAnonymizeItself() {
        save(UserRole.ADMIN);

        assertThrows(IllegalArgumentException.class,
                () -> userService.anonymizeUser(admin.getId(), UserRole.ADMIN, admin.getId()));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isAnonymized()).isFalse();
    }

    @Test
    void refusesToAnonymizeTheLastActiveAdmin() {
        userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN && !user.getId().equals(admin.getId()))
                .forEach(user -> {
                    user.setActive(false);
                    userRepository.save(user);
                });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userService.anonymizeUser(Long.MAX_VALUE, UserRole.ADMIN, admin.getId()));

        assertThat(exception.getMessage()).isEqualTo("É preciso manter ao menos um Administrador ativo");
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isAnonymized()).isFalse();
    }

    @Test
    void anotherAdminCanStillAnonymizeADeactivatedAdminEvenIfTheyAreTheLastActive() {
        User deactivatedAdmin = save(UserRole.ADMIN);
        deactivatedAdmin.setActive(false);
        userRepository.save(deactivatedAdmin);

        userService.anonymizeUser(admin.getId(), UserRole.ADMIN, deactivatedAdmin.getId());

        assertThat(userRepository.findById(deactivatedAdmin.getId()).orElseThrow().isAnonymized()).isTrue();
    }

    @Test
    void anonymizesTheLastActiveCoordinator() {
        userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.COORDENACAO)
                .forEach(user -> {
                    user.setActive(false);
                    userRepository.save(user);
                });
        User onlyCoordinator = save(UserRole.COORDENACAO);

        userService.anonymizeUser(admin.getId(), UserRole.ADMIN, onlyCoordinator.getId());

        assertThat(userRepository.findById(onlyCoordinator.getId()).orElseThrow().isAnonymized()).isTrue();
    }

    @Test
    void refusesToAnonymizeTwice() {
        User professor = save(UserRole.PROFESSOR);
        userService.anonymizeUser(admin.getId(), UserRole.ADMIN, professor.getId());

        assertThrows(IllegalStateException.class,
                () -> userService.anonymizeUser(admin.getId(), UserRole.ADMIN, professor.getId()));
    }

    private User save(UserRole role) {
        return userRepository.save(User.builder()
                .name(role.name() + " " + System.nanoTime())
                .email(role.name().toLowerCase() + "." + System.nanoTime() + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .build());
    }

    private Long saveRoom() {
        return roomRepository.save(Room.builder()
                .roomName("Sala Anonimizacao " + System.nanoTime())
                .roomType("Sala comum")
                .localization("Predio A")
                .roomCapacity(30)
                .description("Sala de teste")
                .active(true)
                .salaEspecial(false)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build()).getId();
    }

    private Reservation saveReservation(Long responsibleId, Long roomId, LocalDate date) {
        return reservationRepository.save(Reservation.builder()
                .roomId(roomId)
                .responsibleId(responsibleId)
                .date(date)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .purpose("Aula")
                .status(ReservationStatus.CONFIRMADA)
                .build());
    }

}
