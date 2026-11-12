package br.com.classholder.classholder.equipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import br.com.classholder.classholder.equipment.EquipmentDeletedEvent;
import br.com.classholder.classholder.equipment.domain.Equipment;
import br.com.classholder.classholder.equipment.dto.EquipmentRequest;
import br.com.classholder.classholder.equipment.dto.EquipmentResponse;
import br.com.classholder.classholder.equipment.repository.EquipmentRepository;
import br.com.classholder.classholder.shared.RecursoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EquipmentService equipmentService;

    @Test
    void shouldCreateEquipmentWhenNameIsUnique() {
        when(equipmentRepository.existsByName("Projetor")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(1L);
            return equipment;
        });

        EquipmentResponse response = equipmentService.createEquipment(new EquipmentRequest("Projetor"));

        assertEquals(1L, response.id());
        assertEquals("Projetor", response.name());
    }

    @Test
    void shouldRejectCreationWhenNameAlreadyExists() {
        when(equipmentRepository.existsByName("Projetor")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> equipmentService.createEquipment(new EquipmentRequest("Projetor")));

        assertEquals("Já existe um equipamento com esse nome", exception.getMessage());
        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void shouldRenameEquipmentWhenNewNameIsUnique() {
        Equipment equipment = Equipment.builder().id(2L).name("Projetor").build();
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(equipment));
        when(equipmentRepository.existsByNameAndIdNot("Projetor 4K", 2L)).thenReturn(false);
        when(equipmentRepository.save(equipment)).thenReturn(equipment);

        EquipmentResponse response = equipmentService.updateEquipment(2L, new EquipmentRequest("Projetor 4K"));

        assertEquals("Projetor 4K", response.name());
    }

    @Test
    void shouldRejectRenameToANameUsedByAnotherEquipment() {
        when(equipmentRepository.findById(2L))
                .thenReturn(Optional.of(Equipment.builder().id(2L).name("Projetor").build()));
        when(equipmentRepository.existsByNameAndIdNot("Computador", 2L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> equipmentService.updateEquipment(2L, new EquipmentRequest("Computador")));
        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdateOfUnknownEquipment() {
        when(equipmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> equipmentService.updateEquipment(99L, new EquipmentRequest("Projetor")));
    }

    @Test
    void shouldUnlinkFromRoomsBeforeDeletingEquipment() {
        when(equipmentRepository.findById(3L))
                .thenReturn(Optional.of(Equipment.builder().id(3L).name("Lousa digital").build()));

        equipmentService.deleteEquipment(3L);

        InOrder order = inOrder(eventPublisher, equipmentRepository);
        order.verify(eventPublisher).publishEvent(new EquipmentDeletedEvent(3L));
        order.verify(equipmentRepository).deleteById(3L);
    }

    @Test
    void shouldRejectDeletionOfUnknownEquipment() {
        when(equipmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> equipmentService.deleteEquipment(99L));
        verify(equipmentRepository, never()).deleteById(any());
    }

    @Test
    void shouldListAllEquipments() {
        when(equipmentRepository.findAll()).thenReturn(List.of(
                Equipment.builder().id(1L).name("Projetor").build(),
                Equipment.builder().id(2L).name("Computador").build()));

        assertThat(equipmentService.listAll()).extracting(EquipmentResponse::name)
                .containsExactly("Projetor", "Computador");
    }

    @Test
    void shouldFindEquipmentById() {
        when(equipmentRepository.findById(1L))
                .thenReturn(Optional.of(Equipment.builder().id(1L).name("Projetor").build()));

        assertEquals("Projetor", equipmentService.findById(1L).name());
    }

}
