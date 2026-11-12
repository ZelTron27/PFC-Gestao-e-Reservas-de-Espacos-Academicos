package br.com.classholder.classholder.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.classholder.classholder.audit.domain.AuditLog;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class AuditLogImmutabilityIntegrationTest {

    private static final String SELECT_RECORD = "SELECT usuario_id, acao, entidade_tipo, entidade_id, entidade_nome, "
            + "CAST(detalhes AS VARCHAR(255)) AS detalhes, timestamp FROM audit_log WHERE id = ?";

    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private AuditLog saved;
    private Map<String, Object> original;

    @BeforeEach
    void saveRecord() {
        saved = auditLogRepository.save(AuditLog.builder()
                .userId(7L)
                .acao("SALA_CRIADA")
                .entidadeTipo("SALA")
                .entidadeId(3L)
                .entidadeNome("Sala 12")
                .detalhes("Motivo: PELA_GESTAO")
                .timestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS))
                .build());
        original = storedRecord();
    }

    @AfterEach
    void removeRecord() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", saved.getId());
    }

    @Test
    void repository_shouldNotExposeDeleteMethods() {
        assertThat(Arrays.stream(AuditLogRepository.class.getMethods()).map(Method::getName))
                .isNotEmpty()
                .noneMatch(name -> name.startsWith("delete"));
    }

    @Test
    void repository_shouldNotInheritBatchOperationsFromCrudRepository() {
        assertThat(CrudRepository.class.isAssignableFrom(AuditLogRepository.class)).isFalse();
    }

    @Test
    void entity_shouldNotExposeSetters() {
        assertThat(Arrays.stream(AuditLog.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void save_withNewInstanceUsingExistingId_shouldNotChangeStoredRecord() {
        auditLogRepository.save(AuditLog.builder()
                .id(saved.getId())
                .userId(99L)
                .acao("ACAO_ALTERADA")
                .entidadeTipo("OUTRO")
                .entidadeId(100L)
                .entidadeNome("Outro nome")
                .detalhes("Outros detalhes")
                .timestamp(Instant.now().plus(1, ChronoUnit.DAYS))
                .build());

        assertThat(storedRecord()).isEqualTo(original);
    }

    @Test
    void save_withDetachedInstanceChangedByReflection_shouldNotChangeStoredRecord() {
        ReflectionTestUtils.setField(saved, "acao", "ACAO_ALTERADA");
        ReflectionTestUtils.setField(saved, "userId", 99L);

        auditLogRepository.save(saved);

        assertThat(storedRecord()).isEqualTo(original);
    }

    @Test
    void commit_withManagedInstanceChangedByReflection_shouldNotChangeStoredRecord() {
        transactionTemplate.executeWithoutResult(status -> {
            AuditLog managed = auditLogRepository.findAll().stream()
                    .filter(auditLog -> saved.getId().equals(auditLog.getId()))
                    .findFirst()
                    .orElseThrow();
            ReflectionTestUtils.setField(managed, "acao", "ACAO_ALTERADA");
            ReflectionTestUtils.setField(managed, "detalhes", "Outros detalhes");
        });

        assertThat(storedRecord()).isEqualTo(original);
    }

    private Map<String, Object> storedRecord() {
        return jdbcTemplate.queryForMap(SELECT_RECORD, saved.getId());
    }

}
