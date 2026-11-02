package br.com.classholder.classholder.room.service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.aop.AuditContext;
import br.com.classholder.classholder.audit.aop.Auditable;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.RoomRequest;
import br.com.classholder.classholder.room.dto.RoomResponse;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.shared.RecursoNaoEncontradoException;

@Service
public class RoomService {

    private static final int SLOT_MINUTES = 30;

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
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

    @Auditable(acao = "SALA_ATUALIZADA", entidadeTipo = "SALA")
    public RoomResponse updateRoom(Long id, RoomRequest request) {
        validateOperatingHours(request.operatingHours());

        Room room = findRoomEntity(id);

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
        AuditContext.setEntidadeId(saved.getId());
        AuditContext.setNome(saved.getRoomName());
        return toResponse(saved);
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
