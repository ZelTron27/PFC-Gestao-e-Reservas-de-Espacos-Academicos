package br.com.classholder.classholder.room.dto;

import java.util.List;
import java.util.Set;

public record RoomResponse(
        Long id,
        String roomName,
        String roomType,
        String localization,
        Integer roomCapacity,
        String description,
        boolean active,
        boolean permiteSolicitacaoAluno,
        boolean salaEspecial,
        Set<Long> equipmentIds,
        List<TimeRange> operatingHours) {
}
