package br.com.classholder.classholder.holiday.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.classholder.classholder.holiday.domain.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByDate(LocalDate date);

    List<Holiday> findAllByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);

    @Modifying
    @Query("delete from Holiday h where h.date between :start and :end")
    int deleteAllInPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);

}
