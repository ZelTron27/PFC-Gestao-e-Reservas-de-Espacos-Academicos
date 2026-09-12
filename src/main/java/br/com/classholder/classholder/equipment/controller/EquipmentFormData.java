package br.com.classholder.classholder.equipment.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EquipmentFormData {

    @NotBlank(message = "Nome do equipamento é obrigatório")
    private String name;

}
