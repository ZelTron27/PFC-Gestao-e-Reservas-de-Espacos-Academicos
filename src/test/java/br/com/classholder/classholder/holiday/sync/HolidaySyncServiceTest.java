package br.com.classholder.classholder.holiday.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.ResourceAccessException;

import br.com.classholder.classholder.holiday.client.FeriadosApiClient;
import br.com.classholder.classholder.holiday.client.FetchedHoliday;
import br.com.classholder.classholder.holiday.domain.HolidaySync;
import br.com.classholder.classholder.holiday.domain.HolidaySyncTrigger;
import br.com.classholder.classholder.holiday.domain.HolidayType;
import br.com.classholder.classholder.holiday.repository.HolidayRepository;
import br.com.classholder.classholder.holiday.repository.HolidaySyncRepository;

class HolidaySyncServiceTest {

    private final FeriadosApiClient client = mock(FeriadosApiClient.class);
    private final HolidayRepository holidayRepository = mock(HolidayRepository.class);
    private final HolidaySyncRepository syncRepository = mock(HolidaySyncRepository.class);
    private final HolidaySyncService service = new HolidaySyncService(client, holidayRepository, syncRepository,
            mock(PlatformTransactionManager.class), 3, 0);

    @BeforeEach
    void setUp() {
        when(syncRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void replacesLocalCopyForBothYearsOnSuccess() {
        when(client.fetchHolidays(anyInt())).thenAnswer(invocation -> christmasOf(invocation.getArgument(0)));

        HolidaySync result = service.synchronize(HolidaySyncTrigger.MANUAL);

        int year = LocalDate.now().getYear();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalHolidays()).isEqualTo(2);
        assertThat(result.getYears()).isEqualTo(year + ", " + (year + 1));
        verify(holidayRepository).deleteAllInPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        verify(holidayRepository).deleteAllInPeriod(LocalDate.of(year + 1, 1, 1), LocalDate.of(year + 1, 12, 31));
        verify(holidayRepository, times(2)).saveAll(anyList());
    }

    @Test
    void keepsLocalCopyAndRecordsFailureWhenApiIsDown() {
        when(client.fetchHolidays(anyInt())).thenThrow(new ResourceAccessException("timeout"));

        HolidaySync result = service.synchronize(HolidaySyncTrigger.AGENDADA);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Não foi possível conectar");
        verify(client, times(3)).fetchHolidays(anyInt());
        verify(holidayRepository, never()).deleteAllInPeriod(any(), any());
        verify(holidayRepository, never()).saveAll(anyList());
    }

    @Test
    void retriesTemporaryFailures() {
        int year = LocalDate.now().getYear();
        when(client.fetchHolidays(year))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(christmasOf(year));
        when(client.fetchHolidays(year + 1)).thenReturn(christmasOf(year + 1));

        HolidaySync result = service.synchronize(HolidaySyncTrigger.INICIALIZACAO);

        assertThat(result.isSuccess()).isTrue();
        verify(client, times(2)).fetchHolidays(year);
    }

    @Test
    void doesNotWipeAYearThatTheApiReturnedEmpty() {
        int year = LocalDate.now().getYear();
        when(client.fetchHolidays(year)).thenReturn(christmasOf(year));
        when(client.fetchHolidays(year + 1)).thenReturn(List.of());

        service.synchronize(HolidaySyncTrigger.AGENDADA);

        verify(holidayRepository, never()).deleteAllInPeriod(LocalDate.of(year + 1, 1, 1), LocalDate.of(year + 1, 12, 31));
    }

    @Test
    void mergeByDateKeepsTheStrongestTypeForRepeatedDates() {
        LocalDate goodFriday = LocalDate.of(2026, 4, 3);
        LocalDate corpusChristi = LocalDate.of(2026, 6, 4);

        List<FetchedHoliday> merged = HolidaySyncService.mergeByDate(List.of(
                new FetchedHoliday(goodFriday, "Sexta-feira Santa", HolidayType.MUNICIPAL),
                new FetchedHoliday(goodFriday, "Sexta-feira Santa", HolidayType.NACIONAL),
                new FetchedHoliday(corpusChristi, "Corpus Christi", HolidayType.FACULTATIVO),
                new FetchedHoliday(corpusChristi, "Corpus Christi", HolidayType.MUNICIPAL)));

        assertThat(merged).extracting(FetchedHoliday::type)
                .containsExactly(HolidayType.NACIONAL, HolidayType.MUNICIPAL);
    }

    private static List<FetchedHoliday> christmasOf(int year) {
        return List.of(new FetchedHoliday(LocalDate.of(year, 12, 25), "Natal", HolidayType.NACIONAL));
    }

}
