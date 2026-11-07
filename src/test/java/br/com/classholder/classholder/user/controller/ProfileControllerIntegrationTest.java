package br.com.classholder.classholder.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileControllerIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$10$KnownHashValueForProfileTestOnly1234567890abcdefghijklmn";
    private static final String TOTP_SECRET = "KNOWNTOTPSECRETBASE32FORTEST";
    private static final LocalDateTime ACCEPTED_AT = LocalDateTime.of(2026, 3, 15, 14, 30);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void profile_asAdmin_shouldShowOwnAccountData() throws Exception {
        assertShowsOwnData(UserRole.ADMIN, "Administrador");
    }

    @Test
    void profile_asCoordenacao_shouldShowOwnAccountData() throws Exception {
        assertShowsOwnData(UserRole.COORDENACAO, "Coordenação");
    }

    @Test
    void profile_asProfessor_shouldShowOwnAccountData() throws Exception {
        assertShowsOwnData(UserRole.PROFESSOR, "Professor");
    }

    @Test
    void profile_shouldNeverExposePasswordHashOrTotpSecret() throws Exception {
        User user = createUser(UserRole.PROFESSOR, TOTP_SECRET, ACCEPTED_AT);

        String html = page(user);

        assertThat(html).doesNotContain(PASSWORD_HASH).doesNotContain(TOTP_SECRET)
                .doesNotContain("failedLoginAttempts").doesNotContain("lockedUntil");
        assertThat(html).contains("Armazenada apenas de forma cifrada (hash)");
    }

    @Test
    void profile_withTwoUsers_shouldShowOnlyTheAuthenticatedUserData() throws Exception {
        User first = createUser(UserRole.PROFESSOR, null, ACCEPTED_AT);
        User second = createUser(UserRole.COORDENACAO, null, ACCEPTED_AT);

        String firstPage = page(first);
        String secondPage = page(second);

        assertThat(firstPage).contains(first.getName()).contains(first.getEmail())
                .doesNotContain(second.getName()).doesNotContain(second.getEmail());
        assertThat(secondPage).contains(second.getName()).contains(second.getEmail())
                .doesNotContain(first.getName()).doesNotContain(first.getEmail());
    }

    @Test
    void profile_withoutAuthentication_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/perfil"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void profile_beforeAcceptingLgpd_shouldRedirectToLgpdLikeOtherProtectedPages() throws Exception {
        User user = createUser(UserRole.PROFESSOR, null, null);

        mockMvc.perform(get("/perfil")
                .with(user(userDetailsService.loadUserByUsername(user.getEmail())))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/lgpd"));

        mockMvc.perform(get("/reservas")
                .with(user(userDetailsService.loadUserByUsername(user.getEmail())))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(redirectedUrl("/lgpd"));
    }

    @Test
    void profile_shouldShowTwoFactorAsConfiguredWhenSecretExists() throws Exception {
        User user = createUser(UserRole.PROFESSOR, TOTP_SECRET, ACCEPTED_AT);

        assertThat(page(user)).contains("Configurada").doesNotContain("Não configurada");
    }

    @Test
    void profile_shouldShowTwoFactorAsNotConfiguredWhenSecretIsMissing() throws Exception {
        User user = createUser(UserRole.PROFESSOR, null, ACCEPTED_AT);

        assertThat(page(user)).contains("Não configurada");
    }

    @Test
    void menu_shouldContainProfileLinkForEveryRoleOnProfileAndOtherPages() throws Exception {
        for (UserRole role : UserRole.values()) {
            User user = createUser(role, null, ACCEPTED_AT);

            assertThat(page(user)).contains("href=\"/perfil\"").contains("Meu perfil");
            assertThat(request("/reservas", user)).contains("href=\"/perfil\"");
        }
    }

    private void assertShowsOwnData(UserRole role, String roleLabel) throws Exception {
        User user = createUser(role, null, ACCEPTED_AT);

        String html = page(user);

        assertThat(html).contains(user.getName()).contains(user.getEmail()).contains(roleLabel)
                .contains("15/03/2026 14:30");
        assertThat(html).contains("Dados da conta").contains("Dados de segurança")
                .contains("Outros dados tratados pelo sistema").contains("href=\"/senha/trocar\"")
                .contains("href=\"/reservas\"").contains("href=\"/lgpd/privacidade\"")
                .contains("href=\"/lgpd/termos\"");
    }

    private String page(User user) throws Exception {
        return request("/perfil", user);
    }

    private String request(String path, User user) throws Exception {
        return mockMvc.perform(get(path)
                .with(user(userDetailsService.loadUserByUsername(user.getEmail())))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private User createUser(UserRole role, String twoFactorSecret, LocalDateTime acceptedAt) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder()
                .name("Pessoa Perfil " + suffix)
                .email("perfil." + suffix + "@classholder.com")
                .passwordHash(PASSWORD_HASH)
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(acceptedAt)
                .twoFactorSecret(twoFactorSecret)
                .build());
    }

}
