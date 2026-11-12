package br.com.classholder.classholder.audit.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.dto.AuditEntry;
import br.com.classholder.classholder.audit.dto.AuditPage;
import br.com.classholder.classholder.audit.dto.AuditSearchCriteria;
import br.com.classholder.classholder.audit.repository.AuditLogSearchRepository;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditLogSearchRepository searchRepository;

    public AuditQueryService(AuditLogSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public AuditPage search(AuditSearchCriteria criteria, int requestedPage, int size) {
        long total = searchRepository.count(criteria);
        int lastPage = total == 0 ? 1 : (int) ((total + size - 1) / size);
        int page = Math.min(Math.max(requestedPage, 1), lastPage);
        List<AuditEntry> entries = searchRepository.search(criteria, (page - 1) * size, size).stream()
                .map(this::toEntry)
                .toList();
        return new AuditPage(entries, total, page, size);
    }

    public List<String> listActions() {
        return searchRepository.findDistinctActions();
    }

    private AuditEntry toEntry(AuditLog log) {
        return new AuditEntry(log.getId(), log.getUserId(), log.getAcao(), log.getEntidadeTipo(),
                log.getEntidadeId(), log.getEntidadeNome(), log.getTimestamp(), log.getDetalhes());
    }

}
