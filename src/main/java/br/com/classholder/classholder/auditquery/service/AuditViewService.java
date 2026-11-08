package br.com.classholder.classholder.auditquery.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.classholder.classholder.audit.dto.AuditEntry;
import br.com.classholder.classholder.audit.dto.AuditPage;
import br.com.classholder.classholder.audit.dto.AuditSearchCriteria;
import br.com.classholder.classholder.audit.dto.AuditSearchCriteria.EntityRef;
import br.com.classholder.classholder.audit.service.AuditQueryService;
import br.com.classholder.classholder.auditquery.dto.AuditView;
import br.com.classholder.classholder.auditquery.dto.AuditView.Option;
import br.com.classholder.classholder.auditquery.dto.AuditView.Row;
import br.com.classholder.classholder.reservation.service.ReservationService;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.service.UserService;

@Service
public class AuditViewService {

    public static final int PAGE_SIZE = 20;
    public static final int DEFAULT_PERIOD_DAYS = 30;

    private static final String SYSTEM_LABEL = "Sistema";
    private static final String UNREGISTERED_LABEL = "E-mail não cadastrado";
    private static final String ROOM_TYPE = "SALA";
    private static final String RESERVATION_TYPE = "RESERVA";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("ACESSO_2FA_FALHA", "Falha na verificação em dois fatores"),
            Map.entry("ACESSO_2FA_SUCESSO", "Verificação em dois fatores concluída"),
            Map.entry("ACESSO_LOGIN_FALHA", "Falha de login"),
            Map.entry("ACESSO_LOGIN_SUCESSO", "Login realizado"),
            Map.entry("ACESSO_LOGOUT", "Logout"),
            Map.entry("EQUIPAMENTO_ATUALIZADO", "Equipamento atualizado"),
            Map.entry("EQUIPAMENTO_CRIADO", "Equipamento criado"),
            Map.entry("EQUIPAMENTO_EXCLUIDO", "Equipamento excluído"),
            Map.entry("LGPD_ACEITO", "Aceite da LGPD"),
            Map.entry("RESERVA_CANCELADA", "Reserva cancelada"),
            Map.entry("RESERVA_CHECKIN", "Check-in realizado"),
            Map.entry("RESERVA_CRIADA", "Reserva criada"),
            Map.entry("SALA_ATUALIZADA", "Sala atualizada"),
            Map.entry("SALA_CRIADA", "Sala criada"),
            Map.entry("SALA_EXCLUIDA", "Sala excluída"),
            Map.entry("SALA_INATIVADA", "Sala inativada"),
            Map.entry("SALA_REATIVADA", "Sala reativada"),
            Map.entry("SENHA_ALTERACAO_FALHA", "Falha na troca de senha"),
            Map.entry("SENHA_ALTERADA", "Senha alterada"),
            Map.entry("SOLICITACAO_APROVADA", "Solicitação aprovada"),
            Map.entry("SOLICITACAO_CRIADA", "Solicitação criada"),
            Map.entry("SOLICITACAO_EXPIRADA", "Solicitação expirada"),
            Map.entry("SOLICITACAO_RECUSADA", "Solicitação recusada"),
            Map.entry("USUARIO_ANONIMIZADO", "Usuário anonimizado"),
            Map.entry("USUARIO_ATUALIZADO", "Usuário atualizado"),
            Map.entry("USUARIO_CRIADO", "Usuário criado"),
            Map.entry("USUARIO_DESATIVADO", "Usuário desativado"),
            Map.entry("USUARIO_REATIVADO", "Usuário reativado"));

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "ACESSO", "Acesso",
            "EQUIPAMENTO", "Equipamento",
            RESERVATION_TYPE, "Reserva",
            ROOM_TYPE, "Sala",
            "USUARIO", "Usuário");

    private final AuditQueryService auditQueryService;
    private final UserService userService;
    private final RoomService roomService;
    private final ReservationService reservationService;
    private final Clock clock;

    public AuditViewService(AuditQueryService auditQueryService, UserService userService, RoomService roomService,
            ReservationService reservationService, Clock clock) {
        this.auditQueryService = auditQueryService;
        this.userService = userService;
        this.roomService = roomService;
        this.reservationService = reservationService;
        this.clock = clock;
    }

    public AuditView buildView(LocalDate requestedFrom, LocalDate requestedTo, String user, Long roomId,
            String action, int page) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate defaultFrom = today.minusDays(DEFAULT_PERIOD_DAYS - 1L);

        LocalDate from = requestedFrom != null ? requestedFrom : defaultFrom;
        LocalDate to = requestedTo != null ? requestedTo : today;
        String periodError = null;
        if (to.isBefore(from)) {
            periodError = "A data final não pode ser anterior à data inicial.";
            from = defaultFrom;
            to = today;
        }

        Long userId = parseUserId(user);
        String normalizedUser = userId == null ? null : userId.toString();
        String normalizedAction = action == null || action.isBlank() ? null : action.strip();

        AuditSearchCriteria criteria = new AuditSearchCriteria(
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(),
                userId,
                normalizedAction,
                roomEntities(roomId));

        AuditPage result = auditQueryService.search(criteria, page, PAGE_SIZE);
        Map<Long, String> userNames = userService.findUserNames(referencedUserIds(result.entries()));
        List<Row> rows = result.entries().stream().map(entry -> toRow(entry, userNames, zone)).toList();

        return new AuditView(from, to, periodError, normalizedUser, roomId, normalizedAction, userOptions(),
                roomOptions(), actionOptions(), rows, result.total(), result.page(), result.totalPages(),
                baseQuery(from, to, normalizedUser, roomId, normalizedAction));
    }

    private Long parseUserId(String user) {
        if (user == null) {
            return null;
        }
        try {
            return Long.valueOf(user.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<EntityRef> roomEntities(Long roomId) {
        if (roomId == null) {
            return null;
        }
        List<EntityRef> entities = new ArrayList<>();
        entities.add(new EntityRef(ROOM_TYPE, List.of(roomId)));
        List<Long> reservationIds = reservationService.listReservationIdsByRoom(roomId);
        if (!reservationIds.isEmpty()) {
            entities.add(new EntityRef(RESERVATION_TYPE, reservationIds));
        }
        return entities;
    }

    private Set<Long> referencedUserIds(List<AuditEntry> entries) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AuditEntry entry : entries) {
            if (entry.userId() != null) {
                ids.add(entry.userId());
            }
            if (AuditEntry.USER_ENTITY_TYPE.equals(entry.entityType()) && entry.entityId() != null) {
                ids.add(entry.entityId());
            }
        }
        return ids;
    }

    private Row toRow(AuditEntry entry, Map<Long, String> userNames, ZoneId zone) {
        return new Row(TIMESTAMP_FORMAT.format(entry.timestamp().atZone(zone)), author(entry, userNames),
                ACTION_LABELS.getOrDefault(entry.action(), entry.action()), item(entry, userNames),
                entry.details());
    }

    private String author(AuditEntry entry, Map<Long, String> userNames) {
        if (entry.userId() == null) {
            return AuditEntry.LOGIN_FAILURE_ACTION.equals(entry.action()) ? UNREGISTERED_LABEL : SYSTEM_LABEL;
        }
        return userName(entry.userId(), userNames);
    }

    private String userName(Long id, Map<Long, String> userNames) {
        String name = userNames.get(id);
        return name != null ? name : "Usuário (ID " + id + ")";
    }

    private String item(AuditEntry entry, Map<Long, String> userNames) {
        String type = entry.entityType() == null ? null
                : TYPE_LABELS.getOrDefault(entry.entityType(), entry.entityType());
        if (type == null) {
            return "";
        }
        if (AuditEntry.USER_ENTITY_TYPE.equals(entry.entityType()) && entry.entityId() != null) {
            return type + ": " + userName(entry.entityId(), userNames);
        }
        if (entry.entityName() != null && !entry.entityName().isBlank()) {
            return type + ": " + entry.entityName();
        }
        return entry.entityId() == null ? type : type + " (ID " + entry.entityId() + ")";
    }

    private List<Option> userOptions() {
        List<Option> options = new ArrayList<>();
        userService.listUsers().stream()
                .sorted(Comparator.comparing(UserResponse::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(user -> options.add(new Option(String.valueOf(user.id()), user.name())));
        return options;
    }

    private List<Option> roomOptions() {
        Map<Long, String> names = new LinkedHashMap<>(roomService.findAllRoomNames());
        List<Long> missing = reservationService.listReservedRoomIds().stream()
                .filter(id -> !names.containsKey(id))
                .toList();
        names.putAll(roomService.findRoomNames(missing));
        return names.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new Option(String.valueOf(entry.getKey()), entry.getValue()))
                .toList();
    }

    private List<Option> actionOptions() {
        return auditQueryService.listActions().stream()
                .map(code -> new Option(code, ACTION_LABELS.getOrDefault(code, code)))
                .sorted(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String baseQuery(LocalDate from, LocalDate to, String user, Long roomId, String action) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .queryParam("de", from)
                .queryParam("ate", to);
        if (user != null) {
            builder.queryParam("usuario", user);
        }
        if (roomId != null) {
            builder.queryParam("sala", roomId);
        }
        if (action != null) {
            builder.queryParam("acao", action);
        }
        return builder.encode().build().getQuery() + "&";
    }

}
