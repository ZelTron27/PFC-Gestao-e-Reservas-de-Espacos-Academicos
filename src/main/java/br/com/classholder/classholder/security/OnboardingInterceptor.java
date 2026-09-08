package br.com.classholder.classholder.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.classholder.classholder.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Esse é o "porteiro" do sistema. Ele roda antes de qualquer página carregar e decide
// se o usuário pode passar ou se tem que ser redirecionado pra terminar alguma etapa
// pendente (aceitar LGPD, configurar o TOTP, ou digitar o código do TOTP nessa sessão).

// Eu criei isso porque o Spring Security sozinho só sabe dizer "autenticado ou não",
// ele não sabe que existe um fluxo de LGPD/TOTP que precisa ser cumprido antes do
// usuário acessar o resto do sistema. Sem esse interceptor, dava pra logar e ir direto
// pra qualquer página digitando a URL, pulando o LGPD e o TOTP.
public class OnboardingInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public OnboardingInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return true;
        }

        String email = authentication.getName();

        // primeiro, precisa ter aceitado o termo de LGPD.
        if (!userService.hasAcceptedLgpdTerm(email)) {
            response.sendRedirect("/lgpd");
            return false;
        }

        // segundo, se é o primeiro login, ainda não configurou o TOTP, manda configurar.
        if (userService.isFirstLogin(email)) {
            response.sendRedirect("/totp/configurar");
            return false;
        }

        // terceiro, já configurou o TOTP antes, mas essa é uma sessão nova, então precisa
        // digitar o código de novo. Guardo isso na sessão (não no banco) porque a verificação
        // vale só enquanto a sessão durar, em uma próxima vez que a pessoa logar, tem que
        // verificar de novo.
        Object totpVerificado = request.getSession().getAttribute(SecuritySessionAttributes.TOTP_VERIFICADO);
        if (!Boolean.TRUE.equals(totpVerificado)) {
            response.sendRedirect("/totp/verificar");
            return false;
        }

        // passou em tudo, pode seguir pra rota que a pessoa pediu.
        return true;
    }

}
