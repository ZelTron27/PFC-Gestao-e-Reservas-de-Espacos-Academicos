package br.com.classholder.classholder.holiday.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import br.com.classholder.classholder.holiday.client.FeriadosApiClient;
import br.com.classholder.classholder.holiday.client.FetchedHoliday;
import br.com.classholder.classholder.holiday.domain.Holiday;
import br.com.classholder.classholder.holiday.domain.HolidaySync;
import br.com.classholder.classholder.holiday.domain.HolidaySyncTrigger;
import br.com.classholder.classholder.holiday.repository.HolidayRepository;
import br.com.classholder.classholder.holiday.repository.HolidaySyncRepository;

@Service
public class HolidaySyncService {

    private static final Logger log = LoggerFactory.getLogger(HolidaySyncService.class);

    private final FeriadosApiClient client;
    private final HolidayRepository holidayRepository;
    private final HolidaySyncRepository syncRepository;
    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final long retryIntervalMs;

    public HolidaySyncService(FeriadosApiClient client, HolidayRepository holidayRepository,
            HolidaySyncRepository syncRepository, PlatformTransactionManager transactionManager,
            @Value("${app.feriados.retry.tentativas}") int maxAttempts,
            @Value("${app.feriados.retry.intervalo-ms}") long retryIntervalMs) {
        this.client = client;
        this.holidayRepository = holidayRepository;
        this.syncRepository = syncRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryIntervalMs = retryIntervalMs;
    }

    public HolidaySync synchronize(HolidaySyncTrigger trigger) {
        int currentYear = LocalDate.now().getYear();
        List<Integer> years = List.of(currentYear, currentYear + 1);

        HolidaySync sync = HolidaySync.builder()
                .executedAt(LocalDateTime.now())
                .trigger(trigger)
                .source(FeriadosApiClient.SOURCE_NAME)
                .years(years.stream().map(String::valueOf).collect(Collectors.joining(", ")))
                .build();

        try {

            Map<Integer, List<FetchedHoliday>> fetchedByYear = new LinkedHashMap<>();
            for (int year : years) {
                fetchedByYear.put(year, fetchWithRetry(year));
            }
            Integer total = transactionTemplate.execute(status -> replaceHolidays(fetchedByYear));
            sync.setSuccess(true);
            sync.setTotalHolidays(total);
            log.info("Feriados sincronizados com a {}: {} datas ({})", FeriadosApiClient.SOURCE_NAME, total,
                    sync.getYears());
        } catch (RuntimeException e) {
            sync.setSuccess(false);
            sync.setMessage(describe(e));
            log.warn("Falha ao sincronizar feriados; mantendo a última cópia local: {}", e.getMessage());
        }

        return syncRepository.save(sync);
    }

    public Optional<HolidaySync> lastSync() {
        return syncRepository.findFirstByOrderByExecutedAtDesc();
    }

    public Optional<HolidaySync> lastSuccessfulSync() {
        return syncRepository.findFirstBySuccessTrueOrderByExecutedAtDesc();
    }

    public List<Holiday> listHolidays(LocalDate start, LocalDate end) {
        return holidayRepository.findAllByDateBetweenOrderByDateAsc(start, end);
    }

    private List<FetchedHoliday> fetchWithRetry(int year) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return client.fetchHolidays(year);
            } catch (HttpClientErrorException | IllegalStateException e) {
                throw e;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("Tentativa {}/{} de buscar feriados de {} falhou: {}", attempt, maxAttempts, year,
                        e.getMessage());
                if (attempt < maxAttempts) {
                    pauseBeforeRetry();
                }
            }
        }
        throw lastError;
    }

    private int replaceHolidays(Map<Integer, List<FetchedHoliday>> fetchedByYear) {
        LocalDateTime syncedAt = LocalDateTime.now();
        int total = 0;

        for (Map.Entry<Integer, List<FetchedHoliday>> entry : fetchedByYear.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            int year = entry.getKey();
            holidayRepository.deleteAllInPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

            List<Holiday> holidays = mergeByDate(entry.getValue()).stream()
                    .map(fetched -> Holiday.builder()
                            .date(fetched.date())
                            .name(fetched.name())
                            .type(fetched.type())
                            .syncedAt(syncedAt)
                            .build())
                    .toList();
            holidayRepository.saveAll(holidays);
            total += holidays.size();
        }

        return total;
    }

    static List<FetchedHoliday> mergeByDate(List<FetchedHoliday> fetched) {
        Map<LocalDate, FetchedHoliday> byDate = new TreeMap<>();
        for (FetchedHoliday holiday : fetched) {
            byDate.merge(holiday.date(), holiday,
                    (current, candidate) -> candidate.type().ordinal() < current.type().ordinal() ? candidate : current);
        }
        return List.copyOf(byDate.values());
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(retryIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sincronização de feriados interrompida", e);
        }
    }

    private static String describe(RuntimeException e) {
        if (e instanceof HttpStatusCodeException http) {
            return FeriadosApiClient.SOURCE_NAME + " respondeu com erro HTTP " + http.getStatusCode().value();
        }
        if (e instanceof ResourceAccessException) {
            return "Não foi possível conectar à " + FeriadosApiClient.SOURCE_NAME + " (sem rede ou tempo esgotado)";
        }
        return e.getMessage();
    }

}
