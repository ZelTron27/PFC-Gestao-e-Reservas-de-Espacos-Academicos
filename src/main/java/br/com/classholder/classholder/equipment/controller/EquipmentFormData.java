package br.com.classholder.classholder.equipment.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EquipmentFormData {

    @NotBlank(message = "Nome do equipamento é obrigatório")
    @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
    private String name;

}
