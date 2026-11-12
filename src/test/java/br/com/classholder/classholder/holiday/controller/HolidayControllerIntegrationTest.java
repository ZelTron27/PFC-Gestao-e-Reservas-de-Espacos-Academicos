package br.com.classholder.classholder.holiday.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;
import br.com.classholder.classholder.holiday.client.FeriadosApiClient;
import br.com.classholder.classholder.holiday.client.FetchedHoliday;
import br.com.classholder.classholder.holiday.domain.HolidaySync;
import br.com.classholder.classholder.holiday.domain.HolidayType;
import br.com.classholder.classholder.holiday.repository.HolidaySyncRepository;
import br.com.classholder.classholder.holiday.sync.HolidaySyncScheduler;
import br.com.classholder.classholder.holiday.sync.HolidaySyncService;
import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HolidayControllerIntegrationTest {

    private static final String SYNC_ACTION = "FERIADOS_SINCRONIZADOS";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private HolidaySyncRepository syncRepository;
    @Autowired
    private HolidaySyncService syncService;
    @Autowired
    private HolidaySyncScheduler syncScheduler;
    @MockitoBean
    private FeriadosApiClient feriadosApiClient;

    @Test
    void coordenacaoSeesThePanelWithoutTheSyncButton() throws Exception {
        User coordenacao = save(UserRole.COORDENACAO);

        mockMvc.perform(get("/feriados")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Sincronizar agora"))));
    }

    @Test
    void coordenacaoCannotTriggerTheManualSync() throws Exception {
        User coordenacao = save(UserRole.COORDENACAO);

        mockMvc.perform(post("/feriados/sincronizar")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesTheSyncButton() throws Exception {
        User admin = save(UserRole.ADMIN);

        mockMvc.perform(get("/feriados")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sincronizar agora")));
    }

    @Test
    void manualSyncWithSuccess_shouldRecordOneAuditEntryWithTheAdminIdAndTheSyncId() throws Exception {
        User admin = save(UserRole.ADMIN);
        when(feriadosApiClient.fetchHolidays(anyInt())).thenAnswer(invocation -> christmasOf(invocation.getArgument(0)));
        long lastLogId = lastAuditLogId();

        synchronizeAs(admin);

        HolidaySync sync = syncRepository.findFirstByOrderByExecutedAtDesc().orElseThrow();
        assertThat(sync.isSuccess()).isTrue();
        List<AuditLog> logs = syncLogsAfter(lastLogId);
        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getUserId()).isEqualTo(admin.getId());
        assertThat(log.getEntidadeTipo()).isEqualTo("FERIADO");
        assertThat(log.getEntidadeId()).isEqualTo(sync.getId());
        assertThat(log.getEntidadeNome()).isEqualTo(sync.getYears());
        assertThat(log.getDetalhes()).isEqualTo("Sucesso: 2 datas carregadas");
        assertThat(log.getEntidadeNome()).doesNotContain(admin.getEmail(), admin.getName());
        assertThat(log.getDetalhes()).doesNotContain(admin.getEmail(), admin.getName());
    }

    @Test
    void manualSyncWithApiFailure_shouldAlsoRecordTheAuditEntry() throws Exception {
        User admin = save(UserRole.ADMIN);
        when(feriadosApiClient.fetchHolidays(anyInt()))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        long lastLogId = lastAuditLogId();

        synchronizeAs(admin);

        HolidaySync sync = syncRepository.findFirstByOrderByExecutedAtDesc().orElseThrow();
        assertThat(sync.isSuccess()).isFalse();
        List<AuditLog> logs = syncLogsAfter(lastLogId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getUserId()).isEqualTo(admin.getId());
        assertThat(logs.get(0).getEntidadeId()).isEqualTo(sync.getId());
        assertThat(logs.get(0).getDetalhes()).startsWith("Falha:").contains("HTTP 401");
    }

    @Test
    void automaticSyncs_shouldNotRecordAuditEntries() {
        when(feriadosApiClient.fetchHolidays(anyInt())).thenAnswer(invocation -> christmasOf(invocation.getArgument(0)));
        long syncsBefore = syncRepository.count();
        long lastLogId = lastAuditLogId();

        new HolidaySyncScheduler(syncService, true).synchronizeOnStartup();
        syncScheduler.synchronizeMonthly();

        assertThat(syncRepository.count()).isEqualTo(syncsBefore + 2);
        assertThat(syncLogsAfter(lastLogId)).isEmpty();
    }

    private void synchronizeAs(User admin) throws Exception {
        mockMvc.perform(post("/feriados/sincronizar")
                .with(user(userDetailsService.loadUserByUsername(admin.getEmail())))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().is3xxRedirection());
    }

    private long lastAuditLogId() {
        return auditLogRepository.findAll().stream().mapToLong(AuditLog::getId).max().orElse(0L);
    }

    private List<AuditLog> syncLogsAfter(long lastLogId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getId() > lastLogId && SYNC_ACTION.equals(log.getAcao()))
                .toList();
    }

    private static List<FetchedHoliday> christmasOf(int year) {
        return List.of(new FetchedHoliday(LocalDate.of(year, 12, 25), "Natal", HolidayType.NACIONAL));
    }

    private User save(UserRole role) {
        return userRepository.save(User.builder()
                .name(role.name() + " " + System.nanoTime())
                .email(role.name().toLowerCase() + "." + System.nanoTime() + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

}
