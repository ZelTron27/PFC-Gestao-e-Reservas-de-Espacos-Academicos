package br.com.classholder.classholder.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.classholder.classholder.room.domain.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByRoomNameAsc();

    List<Room> findAllByActiveTrueAndSalaEspecialFalseOrderByRoomNameAsc();

}
