package br.com.classholder.classholder.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.classholder.classholder.audit.repository.AuditLogRepository;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.dto.UserRequest;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String SENHA_VALIDA = "SenhaForte@2026";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpService totpService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateProfessorWhenDataIsValid() {
        UserRequest request = new UserRequest("Ana Souza", "anaprofessora@classholder.com", SENHA_VALIDA, UserRole.PROFESSOR);
        when(userRepository.existsByEmail("anaprofessora@classholder.com")).thenReturn(false);
        when(passwordEncoder.encode(SENHA_VALIDA)).thenReturn("hash-da-senha");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = userService.createUser(UserRole.ADMIN, request);

        assertEquals(1L, response.id());
        assertEquals("anaprofessora@classholder.com", response.email());
        assertEquals(UserRole.PROFESSOR, response.role());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hash-da-senha", captor.getValue().getPasswordHash());
    }

    @Test
    void shouldThrowExceptionWhenEmailIsAlreadyRegistered() {
        UserRequest request = new UserRequest("Ana Souza", "anaprofessora@classholder.com", SENHA_VALIDA, UserRole.PROFESSOR);
        when(userRepository.existsByEmail("anaprofessora@classholder.com")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(UserRole.ADMIN, request));

        assertEquals("E-mail já cadastrado", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }


    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "COORDENACAO"})
    void shouldRejectWhenCoordenacaoCreatesAnyoneOtherThanProfessor(UserRole role) {
        UserRequest request = new UserRequest("Ana Souza", "ana@classholder.com", SENHA_VALIDA, role);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(UserRole.COORDENACAO, request));

        assertEquals("A coordenação só pode cadastrar professores", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SenhaForte@26",
            "senhaforte@2026",
            "SENHAFORTE@2026",
            "SenhaForte@Abcd",
            "SenhaForte12026"
    })
    void shouldRejectPasswordWhenItDoesNotMeetStrongPasswordPolicy(String senhaInvalida) {
        UserRequest request = new UserRequest("Ana Souza", "anaprofessora@classholder.com", senhaInvalida, UserRole.PROFESSOR);

        when(userRepository.existsByEmail("anaprofessora@classholder.com")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(UserRole.ADMIN, request));

        assertEquals(UserService.PASSWORD_POLICY_MESSAGE, exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldLockAccountFor15MinutesWhenFifthLoginFails() {
        User user = User.builder()
                .id(1L)
                .email("anaprofessora@classholder.com")
                .failedLoginAttempts(4)
                .build();
        when(userRepository.findByEmail("anaprofessora@classholder.com")).thenReturn(Optional.of(user));
        LocalDateTime antes = LocalDateTime.now();

        userService.registerFailedLogin("anaprofessora@classholder.com");

        LocalDateTime depois = LocalDateTime.now();
        assertNotNull(user.getLockedUntil());
        assertTrue(!user.getLockedUntil().isBefore(antes.plusMinutes(15))
                && !user.getLockedUntil().isAfter(depois.plusMinutes(15)));
        assertEquals(0, user.getFailedLoginAttempts());
        verify(userRepository).save(user);
    }

}