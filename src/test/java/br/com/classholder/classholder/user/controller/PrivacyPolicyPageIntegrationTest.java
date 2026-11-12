package br.com.classholder.classholder.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyPolicyPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void privacyPolicy_shouldShowCurrentVersionHeader() throws Exception {
        assertThat(page())
                .contains("Versão 1.2, atualizada em novembro de 2026")
                .doesNotContain("Versão 1.1");
    }

    @Test
    void privacyPolicy_shouldDescribeAuditRecordsWithoutPersonalIdentifiers() throws Exception {
        assertThat(page())
                .contains("(iv) registros de auditoria: identificador numérico do Usuário que realizou a ação, "
                        + "tipo de ação, item afetado, data e hora. Quando a ação envolve outro Usuário, ele também "
                        + "é identificado apenas por seu identificador numérico. Os registros de auditoria não "
                        + "armazenam nome, e-mail nem endereço IP;")
                .doesNotContain("registros de auditoria: e-mail do Usuário");
    }

    @Test
    void privacyPolicy_shouldDescribeFiveYearAuditRetention() throws Exception {
        assertThat(page())
                .contains("(iv) registros de auditoria: mantidos por 5 (cinco) anos a partir da data do registro, "
                        + "prazo necessário à apuração de incidentes e ao exercício regular de direitos em processos "
                        + "judiciais, administrativos ou arbitrais, inclusive trabalhistas. Durante esse período, "
                        + "não são alterados nem excluídos pelo Sistema. Ao final do prazo, são eliminados pela "
                        + "equipe de tecnologia da instituição, conforme o procedimento do manual de implantação. "
                        + "Registros relativos a contas anonimizadas deixam de identificar o titular;")
                .doesNotContain("12 (doze) meses");
    }

    private String page() throws Exception {
        return mockMvc.perform(get("/lgpd/privacidade"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
    }

}
