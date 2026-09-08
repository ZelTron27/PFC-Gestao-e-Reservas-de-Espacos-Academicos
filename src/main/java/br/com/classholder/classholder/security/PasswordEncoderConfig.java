package br.com.classholder.classholder.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// Aqui eu só crio o "bean" (objeto que o Spring gerencia) que criptografa a senha
// antes de salvar no banco. Uso o BCrypt porque ele já é o padrão recomendado:
// cada vez que criptografa a mesma senha dá um resultado diferente (tem "salt" embutido),
// então ninguém consegue comparar hash com hash pra descobrir senha igual.
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
