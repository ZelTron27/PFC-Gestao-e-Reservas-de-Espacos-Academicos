package br.com.classholder.classholder.reservation.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByResponsibleIdOrderByDateDescStartTimeDesc(Long responsibleId);

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

    @Query("select r.roomId from Reservation r where r.id = :id")
    Optional<Long> findRoomIdById(@Param("id") Long id);

    @Query("""
            select count(r) > 0 from Reservation r
            where r.responsibleId = :responsibleId
              and r.roomId = :roomId
              and r.date = :date
              and r.startTime < :endTime
              and r.endTime > :startTime
              and r.status = :status
            """)
    boolean existsOverlappingByResponsible(@Param("responsibleId") Long responsibleId, @Param("roomId") Long roomId,
            @Param("date") LocalDate date, @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime, @Param("status") ReservationStatus status);

    List<Reservation> findAllByStatusOrderByDateAscRoomIdAscStartTimeAscCreatedAtAsc(ReservationStatus status);

    @Query("""
            select r.id from Reservation r
            where r.status = :status
              and (r.createdAt <= :createdUntil
                   or r.date < :today
                   or (r.date = :today and r.startTime <= :now))
            """)
    List<Long> findExpirableIds(@Param("status") ReservationStatus status, @Param("createdUntil") Instant createdUntil,
            @Param("today") LocalDate today, @Param("now") LocalTime now);

    @Query("""
            select r.id from Reservation r
            where r.status = :status
              and r.checkInAt is null
              and (r.date < :limitDate or (r.date = :limitDate and r.startTime <= :limitTime))
            """)
    List<Long> findIdsWithoutCheckInStartedUntil(@Param("status") ReservationStatus status,
            @Param("limitDate") LocalDate limitDate, @Param("limitTime") LocalTime limitTime);

}
