package br.com.classholder.classholder.holiday.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.classholder.classholder.holiday.repository.HolidayRepository;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;

    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public Optional<String> findHolidayDescription(LocalDate date) {
        return holidayRepository.findByDate(date)
                .map(holiday -> holiday.getName() + " (" + holiday.getType().label() + ")");
    }

}
