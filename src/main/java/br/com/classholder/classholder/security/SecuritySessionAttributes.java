package br.com.classholder.classholder.security;

// Classe só pra guardar o nome da chave que uso na sessão pra marcar "esse usuário já
// digitou o código do TOTP nessa sessão". Fiz isso pra não ficar escrevendo a string
// "TOTP_VERIFICADO" solta em vários arquivos e arriscar digitar errado em algum deles.
public final class SecuritySessionAttributes {

    public static final String TOTP_VERIFICADO = "TOTP_VERIFICADO";

    // ninguém precisa instanciar isso, é só uma constante
    private SecuritySessionAttributes() {
    }

}
