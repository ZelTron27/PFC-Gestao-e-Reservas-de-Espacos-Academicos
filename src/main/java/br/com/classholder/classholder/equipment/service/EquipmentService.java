package br.com.classholder.classholder.equipment.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.classholder.classholder.equipment.domain.Equipment;
import br.com.classholder.classholder.equipment.dto.EquipmentRequest;
import br.com.classholder.classholder.equipment.dto.EquipmentResponse;
import br.com.classholder.classholder.equipment.repository.EquipmentRepository;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    public EquipmentService(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
    }

    public EquipmentResponse createEquipment(EquipmentRequest request) {
        if (equipmentRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Já existe um equipamento com esse nome");
        }

        Equipment equipment = Equipment.builder()
                .name(request.name())
                .build();

        Equipment saved = equipmentRepository.save(equipment);
        return new EquipmentResponse(saved.getId(), saved.getName());
    }

    public List<EquipmentResponse> listAll() {
        return equipmentRepository.findAll().stream()
                .map(equipment -> new EquipmentResponse(equipment.getId(), equipment.getName()))
                .toList();
    }

}
