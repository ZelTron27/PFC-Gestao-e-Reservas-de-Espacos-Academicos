package br.com.classholder.classholder.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.classholder.classholder.user.service.UserService;

// Esse arquivo é só o "registro" do OnboardingInterceptor. Um interceptor não faz nada
// sozinho, precisa avisar o Spring que ele existe e em quais rotas ele deve rodar.
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserService userService;

    public WebMvcConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registro o interceptor em todas as rotas, menos nas próprias páginas de
        // lgpd/totp/login/logout/error. Se eu não excluir essas rotas, ele ia tentar
        // redirecionar pra /lgpd de dentro da própria /lgpd e ia entrar em loop infinito.
        registry.addInterceptor(new OnboardingInterceptor(userService))
                .excludePathPatterns("/lgpd", "/lgpd/**", "/totp/**", "/login", "/logout", "/error");
    }



}
