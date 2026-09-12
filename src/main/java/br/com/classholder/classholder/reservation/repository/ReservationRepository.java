package br.com.classholder.classholder.reservation.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByProfessorIdOrderByDateDescStartTimeDesc(Long professorId);

    List<Reservation> findAllByOrderByDateDescStartTimeDesc();

    List<Reservation> findAllByRoomIdAndDateAndStatus(Long roomId, LocalDate date, ReservationStatus status);

    @Query("""
            select r from Reservation r
            where r.roomId = :roomId
              and r.date = :date
              and r.startTime < :endTime
              and r.endTime > :startTime
              and r.status = :status
            """)
    List<Reservation> findConflicting(@Param("roomId") Long roomId, @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
            @Param("status") ReservationStatus status);

}
