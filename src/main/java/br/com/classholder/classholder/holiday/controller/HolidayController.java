package br.com.classholder.classholder.holiday.controller;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.classholder.classholder.holiday.domain.HolidaySync;
import br.com.classholder.classholder.holiday.domain.HolidaySyncTrigger;
import br.com.classholder.classholder.holiday.sync.HolidaySyncService;

@Controller
@RequestMapping("/feriados")
public class HolidayController {

    private final HolidaySyncService syncService;

    public HolidayController(HolidaySyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping
    public String panel(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("hoje", today);
        model.addAttribute("ultimaSincronizacao", syncService.lastSync().orElse(null));
        model.addAttribute("ultimaComSucesso", syncService.lastSuccessfulSync().orElse(null));
        model.addAttribute("feriados", syncService.listHolidays(LocalDate.of(today.getYear(), 1, 1),
                LocalDate.of(today.getYear() + 1, 12, 31)));
        return "feriados/painel";
    }

    @PostMapping("/sincronizar")
    public String synchronize(RedirectAttributes redirectAttributes) {
        HolidaySync result = syncService.synchronize(HolidaySyncTrigger.MANUAL);
        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("sucesso",
                    "Sincronização concluída: " + result.getTotalHolidays() + " datas carregadas.");
        } else {
            redirectAttributes.addFlashAttribute("erro", "A sincronização falhou: " + result.getMessage()
                    + ". Os feriados da última sincronização bem-sucedida continuam valendo.");
        }
        return "redirect:/feriados";
    }

}
