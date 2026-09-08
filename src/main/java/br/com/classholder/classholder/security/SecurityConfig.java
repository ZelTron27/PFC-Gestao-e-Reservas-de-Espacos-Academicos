package br.com.classholder.classholder.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Essa classe deixa explícito quem pode acessar o quê no sistema. Antes eu não tinha
// esse arquivo e o Spring Boot estava usando a configuração padrão dele por trás dos
// panos — funcionava, mas eu não tinha controle nenhum sobre isso escrito no código.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Não existe cadastro público no sistema: quem cria conta é sempre alguém que já
        // está logado (o admin cria a coordenação, a coordenação cria os próprios usuários).
        // Por isso toda rota exige login, sem exceção — nem "/" fica livre pra visitante.
        // O "/login" continua acessível mesmo sem estar logado porque o formLogin() já
        // libera ele automaticamente, não precisei declarar isso na mão.
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults());

        return http.build();
    }

}
