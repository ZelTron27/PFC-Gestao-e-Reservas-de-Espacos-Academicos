package br.com.classholder.classholder.room.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.classholder.classholder.room.domain.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByRoomNameAsc();

    List<Room> findAllByActiveTrueOrderByRoomNameAsc();

    List<Room> findAllByActiveTrueAndSalaEspecialFalseOrderByRoomNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

}
