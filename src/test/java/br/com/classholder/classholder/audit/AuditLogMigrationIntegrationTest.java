package br.com.classholder.classholder.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Tag("integration")
class AuditLogMigrationIntegrationTest {

    private static final String EXISTING_EMAIL = "maria@classholder.com";

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:audit_migration_" + System.nanoTime()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY, email VARCHAR(150) NOT NULL UNIQUE)");
        jdbcTemplate.update("INSERT INTO usuarios (id, email) VALUES (42, ?)", EXISTING_EMAIL);
        run("V16__create_table_audit_log.sql");
        run("V17__alter_table_audit_log_add_entidade_nome.sql");
        run("V18__alter_table_audit_log_drop_usuario_id.sql");
    }

    @Test
    void shouldFillUserIdFromRecordedEmailAndKeepOtherColumns() {
        long id = insertLegacy(EXISTING_EMAIL, "SALA_CRIADA", "SALA", 3L, "Sala 12", "Motivo: PELA_GESTAO");

        migrate();

        Map<String, Object> row = row(id);
        assertThat(row.get("usuario_id")).isEqualTo(42L);
        assertThat(row.get("acao")).isEqualTo("SALA_CRIADA");
        assertThat(row.get("entidade_tipo")).isEqualTo("SALA");
        assertThat(row.get("entidade_id")).isEqualTo(3L);
        assertThat(row.get("entidade_nome")).isEqualTo("Sala 12");
        assertThat(detalhes(id)).isEqualTo("Motivo: PELA_GESTAO");
    }

    @Test
    void shouldLeaveUserIdNullForUnknownEmailAndForSystem() {
        long unknown = insertLegacy("digitado@errado.com", "ACESSO_LOGIN_FALHA", "ACESSO", null, null,
                "IP: 10.0.0.1");
        long system = insertLegacy("SISTEMA", "SOLICITACAO_EXPIRADA", "RESERVA", 8L, "Sala 12 - 2026-10-05 10:00",
                null);

        migrate();

        assertThat(row(unknown).get("usuario_id")).isNull();
        assertThat(row(system).get("usuario_id")).isNull();
        assertThat(row(system).get("entidade_nome")).isEqualTo("Sala 12 - 2026-10-05 10:00");
    }

    @Test
    void shouldRemoveNameAndEmailFromUserEntityButKeepEntityId() {
        long id = insertLegacy(EXISTING_EMAIL, "USUARIO_CRIADO", "USUARIO", 42L,
                "Maria (" + EXISTING_EMAIL + ")", null);

        migrate();

        assertThat(row(id).get("entidade_id")).isEqualTo(42L);
        assertThat(row(id).get("entidade_nome")).isNull();
    }

    @Test
    void shouldRemoveIpInEveryRecordedFormatAndPreserveOtherDetails() {
        long ipv4 = insertLegacy(EXISTING_EMAIL, "ACESSO_LOGIN_SUCESSO", "ACESSO", null, null, "IP: 192.168.0.10");
        long ipv6 = insertLegacy(EXISTING_EMAIL, "ACESSO_LOGOUT", "ACESSO", null, null, "IP: 0:0:0:0:0:0:0:1");
        long mixedAfter = insertLegacy(EXISTING_EMAIL, "RESERVA_CANCELADA", "RESERVA", 1L, null,
                "Motivo: SEM_CHECKIN; IP: 10.0.0.1");
        long mixedBefore = insertLegacy(EXISTING_EMAIL, "RESERVA_CANCELADA", "RESERVA", 2L, null,
                "IP: 10.0.0.1; Motivo: SEM_CHECKIN");
        long unrelated = insertLegacy(EXISTING_EMAIL, "SOLICITACAO_APROVADA", "RESERVA", 3L, null,
                "Solicitações sobrepostas canceladas: [4, 5]");

        migrate();

        assertThat(detalhes(ipv4)).isNull();
        assertThat(detalhes(ipv6)).isNull();
        assertThat(detalhes(mixedAfter)).isEqualTo("Motivo: SEM_CHECKIN");
        assertThat(detalhes(mixedBefore)).isEqualTo("Motivo: SEM_CHECKIN");
        assertThat(detalhes(unrelated)).isEqualTo("Solicitações sobrepostas canceladas: [4, 5]");
    }

    @Test
    void shouldKeepRecordCountAndDropEmailColumn() {
        insertLegacy(EXISTING_EMAIL, "SALA_CRIADA", "SALA", 3L, "Sala 12", null);
        insertLegacy("SISTEMA", "RESERVA_CANCELADA", "RESERVA", 4L, null, "Motivo: SEM_CHECKIN");
        insertLegacy("digitado@errado.com", "ACESSO_LOGIN_FALHA", "ACESSO", null, null, "IP: 10.0.0.1");

        migrate();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'audit_log' AND column_name = 'usuario_email'",
                Long.class)).isZero();
    }

    private long insertLegacy(String email, String acao, String entidadeTipo, Long entidadeId, String entidadeNome,
            String detalhes) {
        jdbcTemplate.update(
                "INSERT INTO audit_log (usuario_email, acao, entidade_tipo, entidade_id, entidade_nome, detalhes) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                email, acao, entidadeTipo, entidadeId, entidadeNome, detalhes);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM audit_log", Long.class);
    }

    private void migrate() {
        run("V26__alter_table_audit_log_replace_usuario_email_with_usuario_id.sql");
    }

    private void run(String migration) {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + migration)).execute(dataSource);
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM audit_log WHERE id = ?", id);
    }

    private String detalhes(long id) {
        return jdbcTemplate.queryForObject("SELECT detalhes FROM audit_log WHERE id = ?", String.class, id);
    }

}
