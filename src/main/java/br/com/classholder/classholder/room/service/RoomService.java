package br.com.classholder.classholder.room.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.RoomRequest;
import br.com.classholder.classholder.room.dto.RoomResponse;
import br.com.classholder.classholder.room.repository.RoomRepository;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public RoomResponse createRoom(RoomRequest request) {
        Room room = Room.builder()
                .roomName(request.roomName())
                .roomType(request.roomType())
                .localization(request.localization())
                .roomCapacity(request.roomCapacity())
                .description(request.description())
                .active(true)
                .permiteSolicitacaoAluno(request.permiteSolicitacaoAluno())
                .salaEspecial(request.salaEspecial())
                .equipmentIds(request.equipmentIds())
                .operatingHours(request.operatingHours())
                .build();

        return toResponse(roomRepository.save(room));
    }

    public RoomResponse updateRoom(Long id, RoomRequest request) {
        Room room = findRoomEntity(id);

        room.setRoomName(request.roomName());
        room.setRoomType(request.roomType());
        room.setLocalization(request.localization());
        room.setRoomCapacity(request.roomCapacity());
        room.setDescription(request.description());
        room.setActive(request.active());
        room.setPermiteSolicitacaoAluno(request.permiteSolicitacaoAluno());
        room.setSalaEspecial(request.salaEspecial());
        room.setEquipmentIds(request.equipmentIds());
        room.setOperatingHours(request.operatingHours());

        return toResponse(roomRepository.save(room));
    }

    public List<RoomResponse> listRooms() {
        return roomRepository.findAllByOrderByRoomNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public RoomResponse findRoomById(Long id) {
        return toResponse(findRoomEntity(id));
    }

    public List<RoomResponse> listActiveCommonRooms() {
        return roomRepository.findAllByActiveTrueAndSalaEspecialFalseOrderByRoomNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private Room findRoomEntity(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sala não encontrada"));
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
                room.getPermiteSolicitacaoAluno(),
                room.getSalaEspecial(),
                room.getEquipmentIds(),
                room.getOperatingHours());
    }

}
