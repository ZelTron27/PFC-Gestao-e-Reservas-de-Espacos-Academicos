package br.com.classholder.classholder.room.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "salas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nome ou identificação da sala (Ex: Sala 1002, 01-T2, etc)
    @NotBlank(message = "Nome (ou identificação) da sala é obrigatório")
    @Column(nullable = false, length = 150)
    private String roomName;

    //Tipo de sala (Ex: Laboratório de química, Eletrônica, Informática, etc)
    @NotBlank(message = "Tipo de sala é obrigatório")
    @Column(nullable = false, length = 250)
    private String roomType;

    //Localização da sala (Ex: Prédio 1, Corredor de design, etc)
    @NotBlank(message = "Localização da sala é obrigatório")
    @Column(nullable = false, length = 350)
    private String localization;

    //Número máximo de cabeças dentro de uma sala de aula (contando geralmente apenas os alunos)
    @NotNull(message = "O número máximo de pessoas é obrigatório") // NotNull no lugar de NotBlank pq o segundo serve mais para string.
    @Min(value = 1, message = "A capacidade da sala não pode ser menor que 1")
    @Column(nullable = false)
    private Integer roomCapacity;

    //Descrição geral das salas / espaços. (texto livre com detalhes adicionais)
    @NotBlank (message = "A descrição da sala de aula é obrigatória")
    @Column(nullable = false, length = 500)
    private String description;

    // Booleano para saber se a sala está ativa ou não.
    @NotNull (message = "É necessário definir se a sala está ativa ou não")
    @Column(nullable = false)
    private Boolean active;
}
