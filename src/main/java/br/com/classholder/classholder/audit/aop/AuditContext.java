package br.com.classholder.classholder.audit.aop;

public final class AuditContext {

    private static final ThreadLocal<String> NOME = new ThreadLocal<>();
    private static final ThreadLocal<Long> ENTIDADE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> DETALHES = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setNome(String nome) {
        NOME.set(nome);
    }

    public static void setEntidadeId(Long entidadeId) {
        ENTIDADE_ID.set(entidadeId);
    }

    public static void setDetalhes(String detalhes) {
        DETALHES.set(detalhes);
    }

    static String consumeNome() {
        String nome = NOME.get();
        NOME.remove();
        return nome;
    }

    static Long consumeEntidadeId() {
        Long entidadeId = ENTIDADE_ID.get();
        ENTIDADE_ID.remove();
        return entidadeId;
    }

    static String consumeDetalhes() {
        String detalhes = DETALHES.get();
        DETALHES.remove();
        return detalhes;
    }

    static void clear() {
        NOME.remove();
        ENTIDADE_ID.remove();
        DETALHES.remove();
    }

}
