package br.com.classholder.classholder.reservation.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.classholder.classholder.reservation.CancellationReason;
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

    @Query("select r.id from Reservation r where r.roomId = :roomId")
    List<Long> findIdsByRoomId(@Param("roomId") Long roomId);

    @Query("select distinct r.roomId from Reservation r")
    List<Long> findDistinctRoomIds();

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

    @Query("""
            select r.id from Reservation r
            where r.responsibleId = :responsibleId
              and r.status in :statuses
              and (r.date > :today or (r.date = :today and r.startTime > :now))
            order by r.roomId, r.id
            """)
    List<Long> findUpcomingIdsByResponsible(@Param("responsibleId") Long responsibleId,
            @Param("statuses") List<ReservationStatus> statuses, @Param("today") LocalDate today,
            @Param("now") LocalTime now);

    @Query("""
            select r.id from Reservation r
            where r.roomId = :roomId
              and r.status in :statuses
              and (r.date > :today or (r.date = :today and r.startTime > :now))
            order by r.id
            """)
    List<Long> findUpcomingIdsByRoom(@Param("roomId") Long roomId,
            @Param("statuses") List<ReservationStatus> statuses, @Param("today") LocalDate today,
            @Param("now") LocalTime now);

    @Query("""
            select r.id from Reservation r
            where r.date = :date
              and r.status in :statuses
              and (r.date > :today or (r.date = :today and r.startTime > :now))
            order by r.id
            """)
    List<Long> findUpcomingIdsByDate(@Param("date") LocalDate date,
            @Param("statuses") List<ReservationStatus> statuses, @Param("today") LocalDate today,
            @Param("now") LocalTime now);

    @Query("""
            select count(r) > 0 from Reservation r
            where r.roomId = :roomId
              and (r.status = :pending
                   or (r.status = :confirmed
                       and (r.checkInAt is null
                            or r.date > :today
                            or (r.date = :today and r.endTime > :now))))
            """)
    boolean existsOpenByRoom(@Param("roomId") Long roomId, @Param("pending") ReservationStatus pending,
            @Param("confirmed") ReservationStatus confirmed, @Param("today") LocalDate today,
            @Param("now") LocalTime now);

    long countByStatus(ReservationStatus status);

    long countByStatusAndDateBetween(ReservationStatus status, LocalDate from, LocalDate to);

    long countByStatusAndCancellationReasonAndDateBetween(ReservationStatus status,
            CancellationReason cancellationReason, LocalDate from, LocalDate to);

    @Query("""
            select count(r) from Reservation r
            where r.status = :status
              and r.date between :from and :to
              and (r.date < :limitDate or (r.date = :limitDate and r.endTime <= :limitTime))
            """)
    long countFinished(@Param("status") ReservationStatus status, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("limitDate") LocalDate limitDate,
            @Param("limitTime") LocalTime limitTime);

    @Query("""
            select count(r) from Reservation r
            where r.status = :status
              and r.date between :from and :to
              and (r.date < :limitDate or (r.date = :limitDate and r.startTime <= :limitTime))
            """)
    long countStartedUntil(@Param("status") ReservationStatus status, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("limitDate") LocalDate limitDate,
            @Param("limitTime") LocalTime limitTime);

    @Query("""
            select r.cancellationReason, count(r) from Reservation r
            where r.status = :status
              and r.date between :from and :to
              and r.cancellationReason is not null
            group by r.cancellationReason
            order by count(r) desc
            """)
    List<Object[]> countCancelledByReason(@Param("status") ReservationStatus status,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select r.roomId, count(r),
                   sum((extract(hour from r.endTime) * 60 + extract(minute from r.endTime))
                       - (extract(hour from r.startTime) * 60 + extract(minute from r.startTime)))
            from Reservation r
            where r.status = :status
              and r.date between :from and :to
            group by r.roomId
            order by sum((extract(hour from r.endTime) * 60 + extract(minute from r.endTime))
                         - (extract(hour from r.startTime) * 60 + extract(minute from r.startTime))) desc,
                     r.roomId asc
            """)
    List<Object[]> summarizeUsageByRoom(@Param("status") ReservationStatus status,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select r.roomId,
                   sum((extract(hour from r.endTime) * 60 + extract(minute from r.endTime))
                       - (extract(hour from r.startTime) * 60 + extract(minute from r.startTime)))
            from Reservation r
            where r.status = :status
              and r.date between :from and :to
              and r.roomId in :roomIds
            group by r.roomId
            """)
    List<Object[]> summarizeUsageForRooms(@Param("status") ReservationStatus status,
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("roomIds") Collection<Long> roomIds);

    @Query("""
            select count(r) from Reservation r
            where r.status in :statuses
              and r.date between :from and :to
              and r.evaluatedById is not null
              and r.evaluatedById <> r.responsibleId
            """)
    long countApprovedRequests(@Param("statuses") List<ReservationStatus> statuses,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select count(r) from Reservation r
            where r.status = :status
              and r.date between :from and :to
              and (r.evaluatedById is null or r.evaluatedById <> r.responsibleId)
            """)
    long countEndedRequests(@Param("status") ReservationStatus status, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(r) from Reservation r
            where r.status in :statuses
              and r.date between :from and :to
              and r.evaluatedById = r.responsibleId
            """)
    long countSelfApprovedRequests(@Param("statuses") List<ReservationStatus> statuses,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select avg(extract(epoch from r.evaluatedAt) - extract(epoch from r.createdAt))
            from Reservation r
            where r.status in :statuses
              and r.date between :from and :to
              and r.evaluatedById is not null
              and r.evaluatedById <> r.responsibleId
              and r.evaluatedAt is not null
            """)
    Double averageApprovalSeconds(@Param("statuses") List<ReservationStatus> statuses,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

}
