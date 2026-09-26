package br.com.classholder.classholder.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import br.com.classholder.classholder.audit.listener.AuditLogoutHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuditLogoutHandler auditLogoutHandler)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/senha/esqueci", "/senha/redefinir").permitAll()
                        .requestMatchers("/salas", "/salas/**").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/equipamentos", "/equipamentos/**").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/usuarios", "/usuarios/**").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/feriados", "/feriados/**").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/reservas/todas", "/reservas/*/cancelar").hasAnyRole("ADMIN", "COORDENACAO")
                        .requestMatchers("/reservas", "/reservas/**").hasAnyRole("ADMIN", "PROFESSOR")
                        .requestMatchers("/lgpd/privacidade", "/lgpd/termos").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                .logout(logout -> logout.addLogoutHandler(auditLogoutHandler));

        return http.build();
    }

}
