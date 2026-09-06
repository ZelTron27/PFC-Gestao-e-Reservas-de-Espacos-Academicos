package br.com.classholder.classholder.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.classholder.classholder.equipment.domain.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

}