package br.com.classholder.classholder.reservation;

public enum CancellationReason {

    PELO_RESPONSAVEL("Cancelada pelo responsável"),
    PELA_GESTAO("Cancelada pela gestão"),
    SEM_CHECKIN("Cancelada por falta de check-in"),
    DIA_BLOQUEADO("Cancelada porque o dia foi bloqueado"),
    HORARIO_APROVADO_PARA_OUTRA("Horário aprovado para outra solicitação"),
    SALA_INATIVADA("Cancelada porque a sala foi inativada"),
    USUARIO_DESATIVADO("Cancelada porque o responsável foi desativado"),
    USUARIO_EXCLUIDO("Cancelada porque o responsável foi excluído");

    private final String label;

    CancellationReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

}
