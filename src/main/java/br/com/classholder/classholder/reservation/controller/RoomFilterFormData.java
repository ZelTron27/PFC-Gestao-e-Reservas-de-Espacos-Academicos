package br.com.classholder.classholder.reservation.controller;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RoomFilterFormData {

    private Set<Long> equipamentos = new HashSet<>();

    @Min(value = 1, message = "A capacidade mínima deve ser maior que zero")
    private Integer capacidadeMinima;

    @Size(max = 250, message = "O tipo deve ter no máximo 250 caracteres")
    private String tipo;

}
