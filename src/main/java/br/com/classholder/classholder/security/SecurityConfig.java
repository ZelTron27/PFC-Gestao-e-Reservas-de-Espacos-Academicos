package br.com.classholder.classholder.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/salas", "/salas/**").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/equipamentos", "/equipamentos/**").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/usuarios", "/usuarios/**").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/reservas/todas", "/reservas/*/cancelar").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/reservas", "/reservas/**").hasAnyRole("ADMIN", "PROFESSOR")
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults());

        return http.build();
    }

}
