package br.com.classholder.classholder.holiday.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import br.com.classholder.classholder.holiday.domain.HolidaySyncTrigger;

@Configuration
@EnableScheduling
public class HolidaySyncScheduler {

    private final HolidaySyncService syncService;
    private final boolean syncOnStartup;

    public HolidaySyncScheduler(HolidaySyncService syncService,
            @Value("${app.feriados.sync-on-startup}") boolean syncOnStartup) {
        this.syncService = syncService;
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeOnStartup() {
        if (syncOnStartup) {
            syncService.synchronize(HolidaySyncTrigger.INICIALIZACAO);
        }
    }

    @Scheduled(cron = "${app.feriados.sync-cron}", zone = "America/Sao_Paulo")
    public void synchronizeDaily() {
        syncService.synchronize(HolidaySyncTrigger.AGENDADA);
    }

}
