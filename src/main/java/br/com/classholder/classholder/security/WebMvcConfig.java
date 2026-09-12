package br.com.classholder.classholder.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.classholder.classholder.user.service.UserService;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserService userService;

    public WebMvcConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new OnboardingInterceptor(userService))
                .excludePathPatterns("/lgpd", "/lgpd/**", "/totp/**", "/senha/trocar", "/login", "/logout", "/error");
    }



}
