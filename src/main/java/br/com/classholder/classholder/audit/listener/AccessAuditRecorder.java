package br.com.classholder.classholder.audit.listener;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;

@Component
class AccessAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AccessAuditRecorder.class);
    private static final String ENTIDADE_TIPO_ACESSO = "ACESSO";

    private final AuditLogRepository auditLogRepository;

    AccessAuditRecorder(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    void record(String acao, Long userId) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .acao(acao)
                .entidadeTipo(ENTIDADE_TIPO_ACESSO)
                .timestamp(Instant.now())
                .build();

        try {
            auditLogRepository.save(auditLog);
        } catch (RuntimeException e) {
            log.error("Falha ao gravar log de auditoria de acesso: acao={}", acao, e);
        }
    }

}
