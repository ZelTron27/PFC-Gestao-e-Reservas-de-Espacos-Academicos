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
import br.com.classholder.classholder.user.dto.UserEditRequest;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminStatusIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .forEach(user -> {
                    user.setActive(false);
                    userRepository.save(user);
                });
    }

    @Test
    void adminDeactivatesAnotherAdmin() {
        User actor = save(UserRole.ADMIN);
        User target = save(UserRole.ADMIN);

        userService.deactivateUser(actor.getId(), UserRole.ADMIN, target.getId());

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void adminReactivatesAnotherAdmin() {
        User actor = save(UserRole.ADMIN);
        User target = save(UserRole.ADMIN);
        target.setActive(false);
        userRepository.save(target);

        userService.reactivateUser(actor.getId(), UserRole.ADMIN, target.getId());

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void coordinatorCannotDeactivateAnAdmin() {
        User coordinator = save(UserRole.COORDENACAO);
        User admin = save(UserRole.ADMIN);
        save(UserRole.ADMIN);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.deactivateUser(coordinator.getId(), UserRole.COORDENACAO, admin.getId()));

        assertThat(exception.getMessage()).isEqualTo("Você não tem permissão para alterar esse usuário");
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void adminCannotEditAnotherAdmin() {
        User actor = save(UserRole.ADMIN);
        User target = save(UserRole.ADMIN);
        String originalEmail = target.getEmail();

        assertThrows(IllegalArgumentException.class, () -> userService.updateUser(actor.getId(), UserRole.ADMIN,
                target.getId(), new UserEditRequest("Outro Nome", "tomada@classholder.com")));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getEmail()).isEqualTo(originalEmail);
    }

    @Test
    void adminCannotDeactivateItself() {
        User actor = save(UserRole.ADMIN);
        save(UserRole.ADMIN);

        assertThrows(IllegalArgumentException.class,
                () -> userService.deactivateUser(actor.getId(), UserRole.ADMIN, actor.getId()));
        assertThat(userRepository.findById(actor.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void refusesToDeactivateTheLastActiveAdmin() {
        User onlyAdmin = save(UserRole.ADMIN);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userService.deactivateUser(Long.MAX_VALUE, UserRole.ADMIN, onlyAdmin.getId()));

        assertThat(exception.getMessage()).isEqualTo("É preciso manter ao menos um Administrador ativo");
        assertThat(userRepository.findById(onlyAdmin.getId()).orElseThrow().isActive()).isTrue();
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
