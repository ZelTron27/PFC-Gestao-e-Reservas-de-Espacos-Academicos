package br.com.classholder.classholder.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoordinatorMinimumIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    private Long adminId;

    @BeforeEach
    void setUp() {
        userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.COORDENACAO)
                .forEach(user -> {
                    user.setActive(false);
                    userRepository.save(user);
                });
        adminId = save(UserRole.ADMIN).getId();
    }

    @Test
    void refusesToDeactivateTheLastActiveCoordinator() {
        User onlyCoordinator = save(UserRole.COORDENACAO);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userService.deactivateUser(adminId, UserRole.ADMIN, onlyCoordinator.getId()));

        assertThat(exception.getMessage()).isEqualTo("É preciso manter ao menos um Coordenador ativo");
        assertThat(userRepository.findById(onlyCoordinator.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void allowsACoordinatorToDeactivateAnotherWhileOneRemainsActive() {
        User actor = save(UserRole.COORDENACAO);
        User target = save(UserRole.COORDENACAO);

        userService.deactivateUser(actor.getId(), UserRole.COORDENACAO, target.getId());

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void allowsDeactivatingAProfessorEvenWithoutOtherCoordinators() {
        User professor = save(UserRole.PROFESSOR);

        userService.deactivateUser(adminId, UserRole.ADMIN, professor.getId());

        assertThat(userRepository.findById(professor.getId()).orElseThrow().isActive()).isFalse();
    }

    private User save(UserRole role) {
        return userRepository.save(User.builder()
                .name(role.name() + " " + System.nanoTime())
                .email(role.name().toLowerCase() + "." + System.nanoTime() + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .build());
    }

}
