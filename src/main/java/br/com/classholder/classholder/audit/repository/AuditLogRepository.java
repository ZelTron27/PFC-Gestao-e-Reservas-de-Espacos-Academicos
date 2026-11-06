package br.com.classholder.classholder.audit.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import br.com.classholder.classholder.audit.domain.AuditLog;

public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findAll();

}
