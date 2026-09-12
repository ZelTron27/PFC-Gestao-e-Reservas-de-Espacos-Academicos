package br.com.classholder.classholder.equipment.dto;

import jakarta.validation.constraints.NotBlank;

public record EquipmentRequest(

        @NotBlank(message = "Nome do equipamento é obrigatório")
        String name) {
}
