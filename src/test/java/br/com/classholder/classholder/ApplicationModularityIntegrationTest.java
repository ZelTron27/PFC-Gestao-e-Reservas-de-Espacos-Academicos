package br.com.classholder.classholder;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

// Apenas um teste para verificar se os modulos estão certos.

@Tag("integration")
class ApplicationModularityIntegrationTest {

    ApplicationModules modules = ApplicationModules.of(ClassholderApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void writesDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
