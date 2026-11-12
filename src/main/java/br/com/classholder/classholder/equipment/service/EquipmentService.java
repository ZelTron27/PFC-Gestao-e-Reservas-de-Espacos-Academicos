package br.com.classholder.classholder.equipment.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.aop.AuditContext;
import br.com.classholder.classholder.audit.aop.Auditable;
import br.com.classholder.classholder.equipment.EquipmentDeletedEvent;
import br.com.classholder.classholder.equipment.domain.Equipment;
import br.com.classholder.classholder.equipment.dto.EquipmentRequest;
import br.com.classholder.classholder.equipment.dto.EquipmentResponse;
import br.com.classholder.classholder.equipment.repository.EquipmentRepository;
import br.com.classholder.classholder.shared.RecursoNaoEncontradoException;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EquipmentService(EquipmentRepository equipmentRepository, ApplicationEventPublisher eventPublisher) {
        this.equipmentRepository = equipmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Auditable(acao = "EQUIPAMENTO_CRIADO", entidadeTipo = "EQUIPAMENTO")
    public EquipmentResponse createEquipment(EquipmentRequest request) {
        if (equipmentRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Já existe um equipamento com esse nome");
        }

        Equipment equipment = Equipment.builder()
                .name(request.name())
                .build();

        Equipment saved = equipmentRepository.save(equipment);
        AuditContext.setEntidadeId(saved.getId());
        AuditContext.setNome(saved.getName());
        return new EquipmentResponse(saved.getId(), saved.getName());
    }

    @Auditable(acao = "EQUIPAMENTO_ATUALIZADO", entidadeTipo = "EQUIPAMENTO")
    public EquipmentResponse updateEquipment(Long id, EquipmentRequest request) {
        Equipment equipment = findEquipment(id);
        if (equipmentRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new IllegalArgumentException("Já existe um equipamento com esse nome");
        }

        equipment.setName(request.name());
        Equipment saved = equipmentRepository.save(equipment);
        AuditContext.setEntidadeId(saved.getId());
        AuditContext.setNome(saved.getName());
        return new EquipmentResponse(saved.getId(), saved.getName());
    }

    @Transactional
    @Auditable(acao = "EQUIPAMENTO_EXCLUIDO", entidadeTipo = "EQUIPAMENTO")
    public void deleteEquipment(Long id) {
        Equipment equipment = findEquipment(id);
        String name = equipment.getName();

        eventPublisher.publishEvent(new EquipmentDeletedEvent(id));
        equipmentRepository.deleteById(id);
        AuditContext.setEntidadeId(id);
        AuditContext.setNome(name);
    }

    public EquipmentResponse findById(Long id) {
        Equipment equipment = findEquipment(id);
        return new EquipmentResponse(equipment.getId(), equipment.getName());
    }

    private Equipment findEquipment(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipamento não encontrado"));
    }

    public List<EquipmentResponse> listAll() {
        return equipmentRepository.findAll().stream()
                .map(equipment -> new EquipmentResponse(equipment.getId(), equipment.getName()))
                .toList();
    }

}
