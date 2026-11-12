package br.com.classholder.classholder.holiday.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.classholder.classholder.holiday.domain.HolidaySync;

public interface HolidaySyncRepository extends JpaRepository<HolidaySync, Long> {

    Optional<HolidaySync> findFirstByOrderByExecutedAtDesc();

    Optional<HolidaySync> findFirstBySuccessTrueOrderByExecutedAtDesc();

}
