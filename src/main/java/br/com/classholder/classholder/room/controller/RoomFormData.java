package br.com.classholder.classholder.room.controller;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ValidRoomFlags
public class RoomFormData {

    @NotBlank(message = "Nome (ou identificação) da sala é obrigatório")
    private String roomName;

    @NotBlank(message = "Tipo de sala é obrigatório")
    private String roomType;

    @NotBlank(message = "Localização da sala é obrigatória")
    private String localization;

    @NotNull(message = "O número máximo de pessoas é obrigatório")
    @Min(value = 1, message = "A capacidade da sala não pode ser menor que 1")
    private Integer roomCapacity;

    @NotBlank(message = "A descrição da sala é obrigatória")
    private String description;

    private boolean permiteSolicitacaoAluno;

    private boolean salaEspecial;

    private boolean active = true;

    private Set<Long> equipmentIds = new HashSet<>();

    private String operatingHoursJson = "[]";

}
