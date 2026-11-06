package br.com.classholder.classholder.audit.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.dto.UserRequest;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.repository.UserRepository;
import br.com.classholder.classholder.user.service.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccessAuditIntegrationTest {

    private static final String PASSWORD = "SenhaForte@2026!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_withValidCredentials_shouldRecordUserIdWithoutIpAndKeepEmailAsAuthenticationName() throws Exception {
        User user = createUser(null);
        int before = countLogs("ACESSO_LOGIN_SUCESSO");

        mockMvc.perform(formLogin("/login").user(user.getEmail()).password(PASSWORD))
                .andExpect(authenticated().withUsername(user.getEmail()));

        AuditLog recorded = singleNewLog("ACESSO_LOGIN_SUCESSO", before);
        assertThat(recorded.getUserId()).isEqualTo(user.getId());
        assertThat(recorded.getDetalhes()).isNull();
        assertThat(recorded.getEntidadeNome()).isNull();
    }

    @Test
    void login_withWrongPasswordForExistingEmail_shouldRecordUserIdOnceWithoutEmail() throws Exception {
        User user = createUser(null);
        int before = countLogs("ACESSO_LOGIN_FALHA");

        mockMvc.perform(formLogin("/login").user(user.getEmail()).password("senha-errada"))
                .andExpect(unauthenticated());

        AuditLog recorded = singleNewLog("ACESSO_LOGIN_FALHA", before);
        assertThat(recorded.getUserId()).isEqualTo(user.getId());
        assertNoPersonalData(recorded, user.getEmail());
    }

    @Test
    void login_withUnknownEmail_shouldRecordNullUserIdAndNeverTheTypedEmail() throws Exception {
        String typedEmail = "desconhecido." + System.nanoTime() + "@classholder.com";
        int before = countLogs("ACESSO_LOGIN_FALHA");

        mockMvc.perform(formLogin("/login").user(typedEmail).password("qualquer-senha"))
                .andExpect(unauthenticated());

        AuditLog recorded = singleNewLog("ACESSO_LOGIN_FALHA", before);
        assertThat(recorded.getUserId()).isNull();
        assertNoPersonalData(recorded, typedEmail);
    }

    @Test
    void login_withLockedAccount_shouldRecordUserIdOnce() throws Exception {
        User user = createUser(LocalDateTime.now().plusMinutes(15));
        int before = countLogs("ACESSO_LOGIN_FALHA");

        mockMvc.perform(formLogin("/login").user(user.getEmail()).password(PASSWORD))
                .andExpect(unauthenticated());

        AuditLog recorded = singleNewLog("ACESSO_LOGIN_FALHA", before);
        assertThat(recorded.getUserId()).isEqualTo(user.getId());
        assertNoPersonalData(recorded, user.getEmail());
    }

    @Test
    void login_withDeactivatedUser_shouldNotAuthenticateAndRecordUserIdOnceWithoutEmail() throws Exception {
        User user = createUser(null);
        user.setActive(false);
        userRepository.save(user);
        int before = countLogs("ACESSO_LOGIN_FALHA");

        mockMvc.perform(formLogin("/login").user(user.getEmail()).password(PASSWORD))
                .andExpect(unauthenticated());

        AuditLog recorded = singleNewLog("ACESSO_LOGIN_FALHA", before);
        assertThat(recorded.getUserId()).isEqualTo(user.getId());
        assertNoPersonalData(recorded, user.getEmail());
    }

    @Test
    void logout_shouldRecordUserIdWithoutIp() throws Exception {
        User user = createUser(null);
        int before = countLogs("ACESSO_LOGOUT");

        mockMvc.perform(post("/logout")
                .with(user(userDetailsService.loadUserByUsername(user.getEmail())))
                .with(csrf()))
                .andExpect(unauthenticated());

        AuditLog recorded = singleNewLog("ACESSO_LOGOUT", before);
        assertThat(recorded.getUserId()).isEqualTo(user.getId());
        assertThat(recorded.getDetalhes()).isNull();
    }

    @Test
    void auditedAction_withLoggedInUser_shouldRecordAuthorIdAndOnlyTheIdOfTheCreatedUser() {
        User admin = createUser(null);
        authenticateAs(admin);
        String createdEmail = "novo." + System.nanoTime() + "@classholder.com";
        int before = countLogs("USUARIO_CRIADO");

        UserResponse created = userService.createUser(UserRole.ADMIN,
                new UserRequest("Pessoa Nova", createdEmail, PASSWORD, UserRole.PROFESSOR));

        AuditLog recorded = singleNewLog("USUARIO_CRIADO", before);
        assertThat(recorded.getUserId()).isEqualTo(admin.getId());
        assertThat(recorded.getEntidadeId()).isEqualTo(created.id());
        assertThat(recorded.getEntidadeNome()).isNull();
    }

    @Test
    void auditedAction_onLgpdAcceptance_shouldRecordOnlyIds() {
        User user = createUser(null);
        authenticateAs(user);
        int before = countLogs("LGPD_ACEITO");

        userService.acceptLgpdTerm(user.getEmail());

        AuditLog recorded = singleNewLog("LGPD_ACEITO", before);
        assertThat(recorded.getUserId()).isEqualTo(user.getId());
        assertThat(recorded.getEntidadeId()).isEqualTo(user.getId());
        assertThat(recorded.getEntidadeNome()).isNull();
    }

    private void authenticateAs(User user) {
        UserDetails principal = userDetailsService.loadUserByUsername(user.getEmail());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    private void assertNoPersonalData(AuditLog recorded, String email) {
        assertThat(recorded.getDetalhes()).isNull();
        assertThat(recorded.getEntidadeNome()).isNull();
        assertThat(recorded.getAcao()).doesNotContain(email);
        assertThat(recorded.getEntidadeTipo()).doesNotContain(email);
    }

    private int countLogs(String acao) {
        return logsOf(acao).size();
    }

    private AuditLog singleNewLog(String acao, int before) {
        List<AuditLog> logs = logsOf(acao);
        assertThat(logs).hasSize(before + 1);
        return logs.get(logs.size() - 1);
    }

    private List<AuditLog> logsOf(String acao) {
        return auditLogRepository.findAll().stream()
                .filter(auditLog -> acao.equals(auditLog.getAcao()))
                .toList();
    }

    private User createUser(LocalDateTime lockedUntil) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder()
                .name("Pessoa Teste " + suffix)
                .email("pessoa." + suffix + "@classholder.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(UserRole.ADMIN)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .lockedUntil(lockedUntil)
                .build());
    }

}
