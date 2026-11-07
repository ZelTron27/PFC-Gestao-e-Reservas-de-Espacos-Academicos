package br.com.classholder.classholder.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void dashboard_asAdmin_shouldShowCards() throws Exception {
        assertShowsCards(createUser(UserRole.ADMIN));
    }

    @Test
    void dashboard_asCoordenacao_shouldShowCards() throws Exception {
        assertShowsCards(createUser(UserRole.COORDENACAO));
    }

    @Test
    void dashboard_asProfessor_shouldReturn403() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);

        mockMvc.perform(request("/dashboard", professor)).andExpect(status().isForbidden());
    }

    @Test
    void dashboard_withoutAuthentication_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void dashboard_withInvalidPeriod_shouldShowMessage() throws Exception {
        User admin = createUser(UserRole.ADMIN);

        String html = mockMvc.perform(request("/dashboard", admin).param("de", "2026-06-10").param("ate", "2026-06-01"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("A data final não pode ser anterior à data inicial.");
    }

    @Test
    void menu_shouldShowDashboardLinkForAdminAndCoordenacaoButNotProfessor() throws Exception {
        assertThat(page("/reservas", createUser(UserRole.ADMIN))).contains("href=\"/dashboard\"");
        assertThat(page("/reservas", createUser(UserRole.COORDENACAO))).contains("href=\"/dashboard\"");
        assertThat(page("/reservas", createUser(UserRole.PROFESSOR))).doesNotContain("href=\"/dashboard\"");
    }

    @Test
    void dashboard_shouldNotExposeUserNamesOrEmails() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        User professor = createUser(UserRole.PROFESSOR);
        Room room = roomRepository.save(Room.builder().roomName("Sala Unica").roomType("Lab")
                .localization("Prédio 1").roomCapacity(10).description("Teste").active(true).build());
        reservationRepository.save(Reservation.builder().roomId(room.getId()).responsibleId(professor.getId())
                .date(LocalDate.now()).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0))
                .purpose("Teste").status(ReservationStatus.CONFIRMADA).build());

        String html = page("/dashboard", admin);

        assertThat(html).contains("Sala Unica");
        assertThat(html.replace(admin.getName(), "").replace(admin.getEmail(), ""))
                .doesNotContain(professor.getName()).doesNotContain(professor.getEmail())
                .doesNotContain("Pessoa Painel").doesNotContain("@classholder.com");
    }

    @Test
    void dashboard_shouldLoadAdminLteAndChartJsOnlyOnItsOwnPage() throws Exception {
        User admin = createUser(UserRole.ADMIN);

        String dashboard = page("/dashboard", admin);
        String reservations = page("/reservas", admin);

        assertThat(dashboard).contains("admin-lte@4.0.2/dist/css/adminlte.min.css").contains("chart.js@4.4.1");
        assertThat(reservations).doesNotContain("adminlte").doesNotContain("chart.js");
    }

    @Test
    void dashboard_withSelectedRooms_shouldRenderComparisonChartWithTheirNames() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        Room first = createRoom("Sala Grafico Um");
        Room second = createRoom("Sala Grafico Dois");
        createRoom("Sala Grafico Tres");

        String html = mockMvc.perform(request("/dashboard", admin)
                .param("salas", first.getId().toString())
                .param("salas", second.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"grafico-salas\"").contains("card-comparativo")
                .contains("Selecione até 5 salas");
        assertThat(html.substring(html.indexOf("const salas"))).contains("Sala Grafico Um")
                .contains("Sala Grafico Dois").doesNotContain("Sala Grafico Tres");
    }

    @Test
    void dashboard_withAllRooms_shouldShowEveryRoomInChartDataAndCheckTheOption() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        for (int index = 1; index <= 7; index++) {
            createRoom("Sala Todas " + index);
        }

        String html = mockMvc.perform(request("/dashboard", admin).param("todas", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Todas as salas").contains("id=\"grafico-salas\"")
                .doesNotContain("Selecione no máximo 5 salas");
        assertThat(html).containsPattern("id=\"sala-todas\"[^>]*checked=\"checked\"");
        String chartData = html.substring(html.indexOf("const salas"));
        for (int index = 1; index <= 7; index++) {
            assertThat(chartData).contains("Sala Todas " + index);
        }
    }

    @Test
    void dashboard_withoutAllRoomsParameter_shouldLeaveTheOptionUnchecked() throws Exception {
        User admin = createUser(UserRole.ADMIN);

        String html = page("/dashboard", admin);

        assertThat(html).contains("id=\"sala-todas\"").doesNotContainPattern("id=\"sala-todas\"[^>]*checked");
    }

    @Test
    void dashboard_withSixRooms_shouldShowMessage() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        var builder = request("/dashboard", admin);
        for (int index = 1; index <= 6; index++) {
            builder.param("salas", String.valueOf(index));
        }

        String html = mockMvc.perform(builder).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Selecione no máximo 5 salas");
    }

    @Test
    void dashboard_withoutReservations_shouldShowNoDataCardAndKeepSelector() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        createRoom("Sala Seletor");

        String html = page("/dashboard", admin);

        assertThat(html).contains("Sem dados no período").contains("Sala Seletor").contains("seletor-salas");
    }

    private Room createRoom(String name) {
        return roomRepository.save(Room.builder().roomName(name).roomType("Lab").localization("Prédio 1")
                .roomCapacity(10).description("Teste").active(true).build());
    }

    private void assertShowsCards(User user) throws Exception {
        String html = page("/dashboard", user);

        assertThat(html).contains("card-confirmadas").contains("card-pendentes").contains("card-ausencia")
                .contains("card-cancelamento").contains("card-motivos").contains("card-salas")
                .contains("card-solicitacoes").contains("card-tempo-aprovacao");
    }

    private String page(String path, User user) throws Exception {
        return mockMvc.perform(request(path, user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,
            User user) {
        return get(path)
                .with(user(userDetailsService.loadUserByUsername(user.getEmail())))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true);
    }

    private User createUser(UserRole role) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder()
                .name("Pessoa Painel " + suffix)
                .email("painel." + suffix + "@classholder.com")
                .passwordHash("$2a$10$KnownHashValueForDashboardTestOnly1234567890abcdefghij")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

}
