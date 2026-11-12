package br.com.classholder.classholder.auditquery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;
import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void audit_asAdmin_shouldReturn200() throws Exception {
        User admin = createUser(UserRole.ADMIN, "Admin Auditoria");

        String html = page("/auditoria", admin);

        assertThat(html).contains("filtros-auditoria").contains("registro(s) encontrado(s)");
    }

    @Test
    void audit_asCoordenacao_shouldBeForbidden() throws Exception {
        User coordination = createUser(UserRole.COORDENACAO, "Coordenacao Auditoria");

        mockMvc.perform(request("/auditoria", coordination)).andExpect(status().isForbidden());
    }

    @Test
    void audit_asProfessor_shouldBeForbidden() throws Exception {
        User professor = createUser(UserRole.PROFESSOR, "Professor Auditoria");

        mockMvc.perform(request("/auditoria", professor)).andExpect(status().isForbidden());
    }

    @Test
    void audit_withoutAuthentication_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/auditoria"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void menu_shouldShowAuditLinkOnlyForAdmin() throws Exception {
        assertThat(page("/reservas", createUser(UserRole.ADMIN, "Admin Menu"))).contains("href=\"/auditoria\"");
        assertThat(page("/reservas", createUser(UserRole.COORDENACAO, "Coord Menu"))).doesNotContain("href=\"/auditoria\"");
        assertThat(page("/reservas", createUser(UserRole.PROFESSOR, "Prof Menu"))).doesNotContain("href=\"/auditoria\"");
    }

    @Test
    void audit_shouldShowDeletedUserLabelForAnonymizedAuthor() throws Exception {
        User admin = createUser(UserRole.ADMIN, "Admin Anonimo");
        User anonymized = createUser(UserRole.PROFESSOR, "Provisorio");
        anonymized.setName("Usuário excluído (ID " + anonymized.getId() + ")");
        userRepository.save(anonymized);
        log(anonymized.getId(), "RESERVA_CRIADA");

        String html = mockMvc.perform(request("/auditoria", admin).param("usuario", anonymized.getId().toString())
                .param("de", LocalDate.now().minusDays(1).toString())
                .param("ate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Usuário excluído (ID " + anonymized.getId() + ")");
    }

    @Test
    void userFilter_shouldListOnlyAllAndRegisteredUsers() throws Exception {
        User admin = createUser(UserRole.ADMIN, "Admin Lista Filtro");
        createUser(UserRole.PROFESSOR, "Professor Lista Filtro");

        String html = page("/auditoria", admin);
        String select = html.substring(html.indexOf("id=\"usuario\""));
        select = select.substring(0, select.indexOf("</select>"));

        assertThat(select).contains("Todos").contains("Admin Lista Filtro").contains("Professor Lista Filtro")
                .doesNotContain("Sistema").doesNotContain("E-mail não cadastrado");
    }

    @Test
    void audit_withNoMatches_shouldShowEmptyMessage() throws Exception {
        User admin = createUser(UserRole.ADMIN, "Admin Vazio");

        String html = mockMvc.perform(request("/auditoria", admin).param("acao", "ACAO_QUE_NAO_EXISTE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Nenhum registro encontrado para os filtros informados.")
                .contains("0 registro(s) encontrado(s)");
    }

    @Test
    void audit_withMoreThanOnePage_shouldRenderPaginationLinksKeepingFilters() throws Exception {
        User admin = createUser(UserRole.ADMIN, "Admin Paginas");
        for (int index = 0; index < 25; index++) {
            log(admin.getId(), "ACAO_PAGINA_TELA");
        }

        String html = mockMvc.perform(request("/auditoria", admin).param("acao", "ACAO_PAGINA_TELA")
                .param("usuario", admin.getId().toString())
                .param("de", LocalDate.now().minusDays(1).toString())
                .param("ate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("25 registro(s) encontrado(s)").contains("pagina=2")
                .contains("acao=ACAO_PAGINA_TELA").contains("usuario=" + admin.getId());
    }

    @Test
    void audit_withInvalidPeriod_shouldShowMessage() throws Exception {
        User admin = createUser(UserRole.ADMIN, "Admin Periodo");

        String html = mockMvc.perform(request("/auditoria", admin).param("de", "2026-06-10").param("ate", "2026-06-01"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("A data final não pode ser anterior à data inicial.");
    }

    @Test
    void audit_shouldNotWriteToTheAuditLog() throws Exception {
        User admin = createUser(UserRole.ADMIN, "Admin Somente Leitura");
        int before = auditLogRepository.findAll().size();

        page("/auditoria", admin);

        assertThat(auditLogRepository.findAll()).hasSize(before);
    }

    private void log(Long userId, String action) {
        auditLogRepository.save(AuditLog.builder().userId(userId).acao(action).entidadeTipo("RESERVA")
                .entidadeId(1L).entidadeNome("Sala X").timestamp(Instant.now()).build());
    }

    private String page(String path, User user) throws Exception {
        return mockMvc.perform(request(path, user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder request(String path, User user) {
        return get(path)
                .with(user(userDetailsService.loadUserByUsername(user.getEmail())))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true);
    }

    private User createUser(UserRole role, String name) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder().name(name).email("auditoria." + suffix + "@classholder.com")
                .passwordHash("x").role(role).firstLogin(false).mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now()).build());
    }

}
