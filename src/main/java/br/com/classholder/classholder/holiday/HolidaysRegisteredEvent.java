package br.com.classholder.classholder.holiday;

import java.time.LocalDate;
import java.util.List;

public record HolidaysRegisteredEvent(List<LocalDate> dates) {

    public HolidaysRegisteredEvent {
        dates = List.copyOf(dates);
    }

}
