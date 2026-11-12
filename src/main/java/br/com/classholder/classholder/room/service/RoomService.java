package br.com.classholder.classholder.room.service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.aop.AuditContext;
import br.com.classholder.classholder.audit.aop.Auditable;
import br.com.classholder.classholder.room.RoomDeletionRequestedEvent;
import br.com.classholder.classholder.room.RoomInactivatedEvent;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.RoomFilter;
import br.com.classholder.classholder.room.dto.RoomRequest;
import br.com.classholder.classholder.room.dto.RoomResponse;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.shared.RecursoNaoEncontradoException;

@Service
public class RoomService {

    private static final int SLOT_MINUTES = 30;

    private final RoomRepository roomRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RoomService(RoomRepository roomRepository, ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.eventPublisher = eventPublisher;
    }

    @Auditable(acao = "SALA_CRIADA", entidadeTipo = "SALA")
    public RoomResponse createRoom(RoomRequest request) {
        validateOperatingHours(request.operatingHours());

        Room room = Room.builder()
                .roomName(request.roomName())
                .roomType(request.roomType())
                .localization(request.localization())
                .roomCapacity(request.roomCapacity())
                .description(request.description())
                .active(true)
                .salaEspecial(request.salaEspecial())
                .equipmentIds(request.equipmentIds())
                .operatingHours(request.operatingHours())
                .build();

        Room saved = roomRepository.save(room);
        AuditContext.setEntidadeId(saved.getId());
        AuditContext.setNome(saved.getRoomName());
        return toResponse(saved);
    }

    @Transactional
    @Auditable(acao = "SALA_ATUALIZADA", entidadeTipo = "SALA")
    public RoomResponse updateRoom(Long id, RoomRequest request) {
        validateOperatingHours(request.operatingHours());

        Room room = lockRoomEntity(id);
        boolean inactivating = room.getActive() && !request.active();

        room.setRoomName(request.roomName());
        room.setRoomType(request.roomType());
        room.setLocalization(request.localization());
        room.setRoomCapacity(request.roomCapacity());
        room.setDescription(request.description());
        room.setActive(request.active());
        room.setSalaEspecial(request.salaEspecial());
        room.setEquipmentIds(request.equipmentIds());
        room.setOperatingHours(request.operatingHours());

        Room saved = roomRepository.save(room);
        if (inactivating) {
            eventPublisher.publishEvent(new RoomInactivatedEvent(id));
        }
        AuditContext.setEntidadeId(saved.getId());
        AuditContext.setNome(saved.getRoomName());
        return toResponse(saved);
    }

    @Transactional
    @Auditable(acao = "SALA_INATIVADA", entidadeTipo = "SALA")
    public void inactivateRoom(Long id) {
        Room room = lockRoomEntity(id);
        if (!room.getActive()) {
            throw new IllegalStateException("Essa sala já está inativa");
        }

        room.setActive(false);
        roomRepository.save(room);
        eventPublisher.publishEvent(new RoomInactivatedEvent(id));
        AuditContext.setEntidadeId(id);
        AuditContext.setNome(room.getRoomName());
    }

    @Transactional
    @Auditable(acao = "SALA_REATIVADA", entidadeTipo = "SALA")
    public void reactivateRoom(Long id) {
        Room room = lockRoomEntity(id);
        if (room.getActive()) {
            throw new IllegalStateException("Essa sala já está ativa");
        }

        room.setActive(true);
        roomRepository.save(room);
        AuditContext.setEntidadeId(id);
        AuditContext.setNome(room.getRoomName());
    }

    @Transactional
    @Auditable(acao = "SALA_EXCLUIDA", entidadeTipo = "SALA")
    public void deleteRoom(Long id) {
        Room room = lockRoomEntity(id);
        if (room.getActive()) {
            throw new IllegalStateException("Só é possível excluir salas inativas");
        }

        eventPublisher.publishEvent(new RoomDeletionRequestedEvent(id));
        roomRepository.delete(room);
        AuditContext.setEntidadeId(id);
        AuditContext.setNome(room.getRoomName());
    }

    public String findRoomName(Long id) {
        return roomRepository.findById(id)
                .map(Room::getRoomName)
                .orElse("Sala excluída (ID " + id + ")");
    }

    public Map<Long, String> findAllRoomNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Object[] row : roomRepository.findAllNames()) {
            names.put((Long) row[0], (String) row[1]);
        }
        return names;
    }

    public Map<Long, String> findActiveRoomNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Object[] row : roomRepository.findActiveNames()) {
            names.put((Long) row[0], (String) row[1]);
        }
        return names;
    }

    public Map<Long, String> findRoomNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (Object[] row : roomRepository.findNamesByIds(ids)) {
            names.put((Long) row[0], (String) row[1]);
        }
        for (Long id : ids) {
            names.computeIfAbsent(id, missing -> "Sala excluída (ID " + missing + ")");
        }
        return names;
    }

    public List<RoomResponse> searchActiveRooms(RoomFilter filter) {
        return listActiveRooms().stream()
                .filter(room -> room.equipmentIds().containsAll(filter.equipmentIds()))
                .filter(room -> filter.minCapacity() == null || room.roomCapacity() >= filter.minCapacity())
                .filter(room -> matchesType(room, filter.roomType()))
                .toList();
    }

    private boolean matchesType(RoomResponse room, String roomType) {
        if (roomType == null || roomType.isBlank()) {
            return true;
        }
        return room.roomType().toLowerCase(Locale.ROOT).contains(roomType.strip().toLowerCase(Locale.ROOT));
    }

    public List<RoomResponse> listRooms() {
        return roomRepository.findAllByOrderByRoomNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public RoomResponse findRoomById(Long id) {
        return toResponse(findRoomEntity(id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RoomResponse findRoomForReservation(Long id) {
        return roomRepository.findByIdForUpdate(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada"));
    }

    public List<RoomResponse> listActiveRooms() {
        return roomRepository.findAllByActiveTrueOrderByRoomNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RoomResponse> listActiveCommonRooms() {
        return roomRepository.findAllByActiveTrueAndSalaEspecialFalseOrderByRoomNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateOperatingHours(List<TimeRange> operatingHours) {
        List<LocalTime[]> ranges = new ArrayList<>();
        for (TimeRange range : operatingHours) {
            LocalTime start = parseSlotTime(range.start());
            LocalTime end = parseSlotTime(range.end());
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException(
                        "No horário de funcionamento, o início deve ser anterior ao fim");
            }
            ranges.add(new LocalTime[] { start, end });
        }

        ranges.sort(Comparator.comparing(range -> range[0]));
        for (int i = 1; i < ranges.size(); i++) {
            if (ranges.get(i)[0].isBefore(ranges.get(i - 1)[1])) {
                throw new IllegalArgumentException("Os intervalos de horário de funcionamento não podem se sobrepor");
            }
        }
    }

    private LocalTime parseSlotTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Preencha o início e o fim de todos os horários de funcionamento");
        }
        LocalTime time;
        try {
            time = LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Horário de funcionamento inválido: " + value);
        }
        if (time.getMinute() % SLOT_MINUTES != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new IllegalArgumentException("Os horários de funcionamento devem ser em blocos de 30 minutos");
        }
        return time;
    }

    private Room lockRoomEntity(Long id) {
        return roomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada"));
    }

    private Room findRoomEntity(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada"));
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getRoomName(),
                room.getRoomType(),
                room.getLocalization(),
                room.getRoomCapacity(),
                room.getDescription(),
                room.getActive(),
                room.getSalaEspecial(),
                room.getEquipmentIds(),
                room.getOperatingHours());
    }

}
