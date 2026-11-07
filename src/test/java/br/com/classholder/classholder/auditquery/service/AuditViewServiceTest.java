package br.com.classholder.classholder.auditquery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;
import br.com.classholder.classholder.audit.service.AuditQueryService;
import br.com.classholder.classholder.auditquery.dto.AuditView;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.reservation.service.ReservationService;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;
import br.com.classholder.classholder.user.service.UserService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditViewServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final Instant NOON = DAY.atTime(12, 0).atZone(ZONE).toInstant();

    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AuditQueryService auditQueryService;
    @Autowired
    private UserService userService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    private AuditViewService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(LocalDate.of(2026, 6, 15), LocalTime.of(10, 0), ZONE).toInstant(),
                ZONE);
        service = new AuditViewService(auditQueryService, userService, roomService, reservationService, clock);
    }

    @Test
    void author_withExistingUser_shouldShowCurrentName() {
        User user = createUser("Ana Autora");
        log(user.getId(), "RESERVA_CRIADA", "RESERVA", 5L, "Sala X", NOON);

        AuditView view = view(user.getId().toString(), null, null);

        assertThat(view.rows()).extracting("author").containsExactly("Ana Autora");
    }

    @Test
    void author_withAnonymizedUser_shouldShowDeletedUserLabel() {
        User user = createUser("Nome Provisorio");
        user.setName("Usuário excluído (ID " + user.getId() + ")");
        userRepository.save(user);
        log(user.getId(), "RESERVA_CRIADA", "RESERVA", 5L, "Sala X", NOON);

        AuditView view = view(user.getId().toString(), null, null);

        assertThat(view.rows()).extracting("author").containsExactly("Usuário excluído (ID " + user.getId() + ")");
    }

    @Test
    void author_withUnknownUserId_shouldShowUserWithId() {
        log(424242L, "RESERVA_CRIADA", "RESERVA", 5L, "Sala X", NOON);

        AuditView view = view("424242", null, null);

        assertThat(view.rows()).extracting("author").containsExactly("Usuário (ID 424242)");
    }

    @Test
    void author_withoutUser_shouldShowSystemExceptForLoginFailure() {
        log(null, "SOLICITACAO_EXPIRADA", "RESERVA", 5L, "Sala X", NOON);
        log(null, "ACESSO_LOGIN_FALHA", "ACESSO", null, null, NOON);

        AuditView system = view(null, null, "SOLICITACAO_EXPIRADA");
        AuditView unregistered = view(null, null, "ACESSO_LOGIN_FALHA");
        AuditView everything = view(null, null, null);

        assertThat(system.rows()).extracting("author", "action")
                .containsExactly(tuple("Sistema", "Solicitação expirada"));
        assertThat(unregistered.rows()).extracting("author", "action")
                .containsExactly(tuple("E-mail não cadastrado", "Falha de login"));
        assertThat(everything.rows()).extracting("author")
                .containsExactlyInAnyOrder("Sistema", "E-mail não cadastrado");
    }

    @Test
    void userFilter_withLegacySpecialValues_shouldBehaveAsAll() {
        log(null, "SOLICITACAO_EXPIRADA", "RESERVA", 5L, "Sala X", NOON);
        log(null, "ACESSO_LOGIN_FALHA", "ACESSO", null, null, NOON);
        User author = createUser("Autor Legado");
        log(author.getId(), "RESERVA_CRIADA", "RESERVA", 6L, "Sala Y", NOON);

        AuditView all = view(null, null, null);
        AuditView legacySystem = view("SISTEMA", null, null);
        AuditView legacyUnregistered = view("EMAIL_NAO_CADASTRADO", null, null);

        assertThat(legacySystem.rows()).isEqualTo(all.rows());
        assertThat(legacyUnregistered.rows()).isEqualTo(all.rows());
        assertThat(legacySystem.selectedUser()).isNull();
        assertThat(legacySystem.baseQuery()).doesNotContain("usuario=");
        assertThat(all.rows()).hasSize(3);
    }

    @Test
    void affectedItem_shouldUseCurrentUserNameForUsersAndStoredNameOrIdForOthers() {
        User admin = createUser("Admin Atual");
        User target = createUser("Alvo Atual");
        log(admin.getId(), "USUARIO_ATUALIZADO", "USUARIO", target.getId(), null, NOON);
        log(admin.getId(), "SALA_ATUALIZADA", "SALA", 9L, "Sala Gravada", NOON);
        log(admin.getId(), "SALA_CRIADA", "SALA", 10L, null, NOON);

        AuditView view = view(admin.getId().toString(), null, null);

        assertThat(view.rows()).extracting("item")
                .containsExactlyInAnyOrder("Usuário: Alvo Atual", "Sala: Sala Gravada", "Sala (ID 10)");
    }

    @Test
    void details_shouldBeShownAsStored() {
        User user = createUser("Autor Detalhes");
        auditLogRepository.save(AuditLog.builder().userId(user.getId()).acao("RESERVA_CANCELADA")
                .entidadeTipo("RESERVA").entidadeId(1L).timestamp(NOON).detalhes("Motivo: PELA_GESTAO").build());

        assertThat(view(user.getId().toString(), null, null).rows()).extracting("details")
                .containsExactly("Motivo: PELA_GESTAO");
    }

    @Test
    void userFilter_shouldIncludeRecordsWhereTheUserIsTheAuthorOrTheAffectedEntity() {
        User first = createUser("Primeiro");
        User second = createUser("Segundo");
        log(first.getId(), "A_FEITA_PELO_PRIMEIRO", "SALA", 1L, "S", NOON);
        log(second.getId(), "USUARIO_ATUALIZADO", "USUARIO", first.getId(), null, NOON);
        log(second.getId(), "A_FEITA_PELO_SEGUNDO", "SALA", 1L, "S", NOON);

        AuditView view = view(first.getId().toString(), null, null);

        assertThat(view.rows()).extracting("action")
                .containsExactlyInAnyOrder("A_FEITA_PELO_PRIMEIRO", "Usuário atualizado");
    }

    @Test
    void roomFilter_shouldReturnRoomRecordsAndRecordsOfItsReservationsButNotOfOtherRooms() {
        Room room = createRoom("Sala Filtro");
        Room other = createRoom("Sala Outra");
        Reservation reservation = createReservation(room.getId());
        Reservation otherReservation = createReservation(other.getId());
        User author = createUser("Autor Sala");
        log(author.getId(), "SALA_ATUALIZADA", "SALA", room.getId(), "R-sala", NOON);
        log(author.getId(), "RESERVA_CRIADA", "RESERVA", reservation.getId(), "R-reserva", NOON);
        log(author.getId(), "SALA_ATUALIZADA", "SALA", other.getId(), "O-sala", NOON);
        log(author.getId(), "RESERVA_CRIADA", "RESERVA", otherReservation.getId(), "O-reserva", NOON);

        AuditView view = service.buildView(DAY, DAY, author.getId().toString(), room.getId(), null, 1);

        assertThat(view.rows()).extracting("item").containsExactlyInAnyOrder("Sala: R-sala", "Reserva: R-reserva");
    }

    @Test
    void roomFilter_forRoomWithoutReservations_shouldReturnOnlyRoomRecords() {
        Room room = createRoom("Sala Sem Reservas");
        User author = createUser("Autor Sala Vazia");
        log(author.getId(), "SALA_CRIADA", "SALA", room.getId(), "Sala Sem Reservas", NOON);

        AuditView view = service.buildView(DAY, DAY, author.getId().toString(), room.getId(), null, 1);

        assertThat(view.rows()).hasSize(1);
    }

    @Test
    void actionFilter_shouldReturnOnlyThatAction() {
        User author = createUser("Autor Acao");
        log(author.getId(), "RESERVA_CRIADA", "RESERVA", 1L, "A", NOON);
        log(author.getId(), "RESERVA_CANCELADA", "RESERVA", 2L, "B", NOON);

        AuditView view = view(author.getId().toString(), null, "RESERVA_CANCELADA");

        assertThat(view.rows()).extracting("action").containsExactly("Reserva cancelada");
    }

    @Test
    void periodFilter_shouldIncludeBothDayBoundariesAndExcludeNeighbouringDays() {
        User author = createUser("Autor Periodo");
        Instant startOfDay = DAY.atStartOfDay(ZONE).toInstant();
        Instant endOfDay = DAY.plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1);
        log(author.getId(), "NO_INICIO", "SALA", 1L, "I", startOfDay);
        log(author.getId(), "NO_FIM", "SALA", 1L, "F", endOfDay);
        log(author.getId(), "ANTES", "SALA", 1L, "A", startOfDay.minusMillis(1));
        log(author.getId(), "DEPOIS", "SALA", 1L, "D", endOfDay.plusMillis(1));

        AuditView view = view(author.getId().toString(), null, null);

        assertThat(view.rows()).extracting("action").containsExactlyInAnyOrder("NO_INICIO", "NO_FIM");
    }

    @Test
    void combinedFilters_shouldApplyAllOfThem() {
        User first = createUser("Combinado Um");
        User second = createUser("Combinado Dois");
        log(first.getId(), "ACAO_X", "SALA", 1L, "certo", NOON);
        log(first.getId(), "ACAO_Y", "SALA", 1L, "outra acao", NOON);
        log(second.getId(), "ACAO_X", "SALA", 1L, "outro autor", NOON);
        log(first.getId(), "ACAO_X", "SALA", 1L, "outro dia", NOON.plusSeconds(86400 * 3));

        AuditView view = view(first.getId().toString(), null, "ACAO_X");

        assertThat(view.rows()).extracting("item").containsExactly("Sala: certo");
    }

    @Test
    void pagination_shouldSplitInPagesOfTwentyNewestFirstAndPreserveFiltersInLinks() {
        User author = createUser("Autor Paginas");
        for (int index = 0; index < 45; index++) {
            log(author.getId(), "ACAO_PAGINADA", "SALA", 1L, "item " + index, NOON.plusSeconds(index));
        }

        AuditView first = service.buildView(DAY, DAY, author.getId().toString(), null, "ACAO_PAGINADA", 1);
        AuditView last = service.buildView(DAY, DAY, author.getId().toString(), null, "ACAO_PAGINADA", 3);
        AuditView beyond = service.buildView(DAY, DAY, author.getId().toString(), null, "ACAO_PAGINADA", 99);

        assertThat(first.total()).isEqualTo(45);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.rows()).hasSize(20);
        assertThat(first.rows().get(0).item()).isEqualTo("Sala: item 44");
        assertThat(last.rows()).hasSize(5);
        assertThat(last.rows().get(4).item()).isEqualTo("Sala: item 0");
        assertThat(beyond.page()).isEqualTo(3);
        assertThat(first.baseQuery()).contains("de=2026-03-10").contains("ate=2026-03-10")
                .contains("usuario=" + author.getId()).contains("acao=ACAO_PAGINADA").endsWith("&");
    }

    @Test
    void period_withEndBeforeStart_shouldReturnMessageAndLastThirtyDays() {
        AuditView view = service.buildView(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1), null, null, null, 1);

        assertThat(view.periodError()).isEqualTo("A data final não pode ser anterior à data inicial.");
        assertThat(view.from()).isEqualTo(LocalDate.of(2026, 5, 17));
        assertThat(view.to()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void period_withoutParameters_shouldDefaultToLastThirtyDaysIncludingToday() {
        AuditView view = service.buildView(null, null, null, null, null, 1);

        assertThat(view.periodError()).isNull();
        assertThat(view.from()).isEqualTo(LocalDate.of(2026, 5, 17));
        assertThat(view.to()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void userOptions_shouldContainOnlyRegisteredUsers() {
        createUser("Opcao Usuario Um");
        createUser("Opcao Usuario Dois");

        AuditView view = view(null, null, null);

        assertThat(view.users()).extracting("label")
                .containsExactlyInAnyOrderElementsOf(
                        userService.listUsers().stream().map(user -> user.name()).toList())
                .contains("Opcao Usuario Um", "Opcao Usuario Dois")
                .doesNotContain("Sistema", "E-mail não cadastrado");
        assertThat(view.users()).allMatch(option -> option.value().chars().allMatch(Character::isDigit));
    }

    @Test
    void options_shouldListRoomsIncludingDeletedOnesAndActions() {
        createRoom("Opcao Sala");
        createReservation(776655L);
        log(null, "ACAO_DA_LISTA", "SALA", 1L, "x", NOON);

        AuditView view = view(null, null, null);

        assertThat(view.rooms()).extracting("label").contains("Opcao Sala", "Sala excluída (ID 776655)");
        assertThat(view.actions()).extracting("value").contains("ACAO_DA_LISTA");
    }

    @Test
    void view_shouldNotChangeTheAuditLog() {
        User author = createUser("Autor Leitura");
        log(author.getId(), "ACAO_LEITURA", "SALA", 1L, "x", NOON);
        long before = auditLogRepository.findAll().size();

        view(author.getId().toString(), null, null);
        service.buildView(null, null, null, null, null, 1);

        assertThat(auditLogRepository.findAll()).hasSize((int) before);
    }

    private AuditView view(String user, Long roomId, String action) {
        return service.buildView(DAY, DAY, user, roomId, action, 1);
    }

    private void log(Long userId, String action, String type, Long entityId, String entityName, Instant timestamp) {
        auditLogRepository.save(AuditLog.builder().userId(userId).acao(action).entidadeTipo(type)
                .entidadeId(entityId).entidadeNome(entityName).timestamp(timestamp).build());
    }

    private User createUser(String name) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder().name(name).email("audit." + suffix + "@classholder.com")
                .passwordHash("x").role(UserRole.PROFESSOR).firstLogin(false).mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now()).build());
    }

    private Room createRoom(String name) {
        return roomRepository.save(Room.builder().roomName(name).roomType("Lab").localization("P1")
                .roomCapacity(10).description("d").active(true).build());
    }

    private Reservation createReservation(Long roomId) {
        return reservationRepository.save(Reservation.builder().roomId(roomId).responsibleId(1L)
                .date(DAY).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).purpose("p")
                .status(ReservationStatus.CONFIRMADA).build());
    }

}
