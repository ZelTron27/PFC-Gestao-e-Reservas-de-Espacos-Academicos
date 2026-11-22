package br.com.classholder.classholder.room.dto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record RoomFilter(Set<Long> equipmentIds, Integer minCapacity, String roomType, String roomName) {

    public RoomFilter {
        equipmentIds = equipmentIds == null ? Set.of()
                : equipmentIds.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

}
