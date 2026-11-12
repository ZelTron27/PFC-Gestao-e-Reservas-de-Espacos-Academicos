package br.com.classholder.classholder.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import br.com.classholder.classholder.audit.aop.AuditPrincipal;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl service;

    @Test
    void loadUserByUsername_withInactiveUser_shouldBeDisabledAndExposeAuditPrincipal() {
        UserDetails details = load(false, 70L);

        assertThat(details.isEnabled()).isFalse();
        assertThat(details).isInstanceOf(AuditPrincipal.class);
        assertThat(((AuditPrincipal) details).getUserId()).isEqualTo(7L);
    }

    @Test
    void loadUserByUsername_withActiveUser_shouldBeEnabledAndExposeAuditPrincipal() {
        UserDetails details = load(true, 8L);

        assertThat(details.isEnabled()).isTrue();
        assertThat(details).isInstanceOf(AuditPrincipal.class);
        assertThat(((AuditPrincipal) details).getUserId()).isEqualTo(8L);
    }

    private UserDetails load(boolean active, Long id) {
        User user = User.builder()
                .id(id)
                .name("Pessoa")
                .email("pessoa@classholder.com")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .active(active)
                .build();
        when(userRepository.findByEmail("pessoa@classholder.com")).thenReturn(Optional.of(user));
        return service.loadUserByUsername("pessoa@classholder.com");
    }

}
