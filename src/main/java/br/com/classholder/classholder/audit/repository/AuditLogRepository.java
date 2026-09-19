package br.com.classholder.classholder.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.classholder.classholder.audit.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
