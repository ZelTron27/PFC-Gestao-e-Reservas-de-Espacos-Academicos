package br.com.classholder.classholder.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.classholder.classholder.holiday.service.HolidayService;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.user.service.UserService;

class ReservationServiceDayRulesTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);
    private static final LocalDate CORPUS_CHRISTI = LocalDate.of(2026, 6, 4);

    private final HolidayService holidayService = mock(HolidayService.class);

    @Test
    void blocksSundayAlways() {
        assertThat(service(false).findUnavailableDayReason(SUNDAY)).contains("Não há reservas aos domingos");
    }

    @Test
    void blocksSaturdayOnlyWhenConfigured() {
        when(holidayService.findHolidayDescription(any())).thenReturn(Optional.empty());

        assertThat(service(true).findUnavailableDayReason(SATURDAY)).contains("Não há reservas aos sábados");
        assertThat(service(false).findUnavailableDayReason(SATURDAY)).isEmpty();
    }

    @Test
    void blocksHolidaysFromTheLocalCopy() {
        when(holidayService.findHolidayDescription(CORPUS_CHRISTI))
                .thenReturn(Optional.of("Corpus Christi (feriado municipal)"));

        assertThat(service(true).findUnavailableDayReason(CORPUS_CHRISTI))
                .contains("Não é possível reservar em 04/06/2026: Corpus Christi (feriado municipal)");
    }

    @Test
    void allowsRegularWeekday() {
        when(holidayService.findHolidayDescription(WEDNESDAY)).thenReturn(Optional.empty());

        assertThat(service(true).findUnavailableDayReason(WEDNESDAY)).isEmpty();
    }

    private ReservationService service(boolean blockSaturday) {
        return new ReservationService(mock(ReservationRepository.class), mock(RoomService.class),
                mock(UserService.class), holidayService, blockSaturday);
    }

}
