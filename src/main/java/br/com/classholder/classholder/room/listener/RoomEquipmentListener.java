package br.com.classholder.classholder.room.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.com.classholder.classholder.equipment.EquipmentDeletedEvent;
import br.com.classholder.classholder.room.repository.RoomRepository;

@Component
public class RoomEquipmentListener {

    private final RoomRepository roomRepository;

    public RoomEquipmentListener(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @EventListener
    public void onEquipmentDeleted(EquipmentDeletedEvent event) {
        roomRepository.removeEquipment(event.equipmentId());
    }

}
