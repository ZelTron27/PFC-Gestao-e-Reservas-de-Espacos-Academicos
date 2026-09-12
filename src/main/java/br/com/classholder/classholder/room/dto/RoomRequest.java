package br.com.classholder.classholder.room.dto;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoomRequest(

        @NotBlank(message = "Nome (ou identificação) da sala é obrigatório")
        String roomName,

        @NotBlank(message = "Tipo de sala é obrigatório")
        String roomType,

        @NotBlank(message = "Localização da sala é obrigatória")
        String localization,

        @NotNull(message = "O número máximo de pessoas é obrigatório")
        @Min(value = 1, message = "A capacidade da sala não pode ser menor que 1")
        Integer roomCapacity,

        @NotBlank(message = "A descrição da sala é obrigatória")
        String description,

        boolean permiteSolicitacaoAluno,

        boolean salaEspecial,

        boolean active,

        Set<Long> equipmentIds,

        List<TimeRange> operatingHours) {

    public RoomRequest {
        if (equipmentIds == null) {
            equipmentIds = Set.of();
        }
        if (operatingHours == null) {
            operatingHours = List.of();
        }
    }

}
