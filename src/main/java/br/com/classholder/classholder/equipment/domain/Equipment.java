package br.com.classholder.classholder.equipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "equipamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nome do equipamento (Ex: Projetor, Computador, Lousa digital, etc)
    // Unique no column serve pra não ter 300 itens com o nome "Projetor" por exemplo.
    @NotBlank(message = "Nome do equipamento é obrigatório")
    @Column(nullable = false, unique = true, length = 150)
    private String name;
}