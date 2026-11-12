package br.com.classholder.classholder.room.dto;

import java.util.Set;

public record RoomFilter(Set<Long> equipmentIds, Integer minCapacity, String roomType) {

    public RoomFilter {
        if (equipmentIds == null) {
            equipmentIds = Set.of();
        }
    }

}
