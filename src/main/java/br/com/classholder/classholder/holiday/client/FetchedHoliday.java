package br.com.classholder.classholder.holiday.client;

import java.time.LocalDate;

import br.com.classholder.classholder.holiday.domain.HolidayType;

public record FetchedHoliday(LocalDate date, String name, HolidayType type) {
}
