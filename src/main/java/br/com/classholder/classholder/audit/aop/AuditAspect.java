package br.com.classholder.classholder.audit.aop;

import java.time.Instant;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @AfterReturning(pointcut = "@annotation(auditable)")
    public void audit(Auditable auditable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication != null ? authentication.getName() : null;

        AuditLog auditLog = AuditLog.builder()
                .usuarioEmail(email)
                .acao(auditable.acao())
                .entidadeTipo(auditable.entidadeTipo())
                .entidadeId(AuditContext.consumeEntidadeId())
                .entidadeNome(AuditContext.consumeNome())
                .timestamp(Instant.now())
                .build();

        try {
            auditLogRepository.save(auditLog);
        } catch (RuntimeException e) {
            log.error("Falha ao gravar log de auditoria: acao={}, entidadeTipo={}, entidadeId={}",
                    auditable.acao(), auditable.entidadeTipo(), auditLog.getEntidadeId(), e);
        }
    }

    @AfterThrowing("@annotation(br.com.classholder.classholder.audit.aop.Auditable)")
    public void clearContextOnFailure() {
        AuditContext.clear();
    }

}
