package br.com.classholder.classholder.holiday.domain;

public enum HolidaySyncTrigger {

    INICIALIZACAO("Inicialização do sistema"),
    AGENDADA("Agendada (diária)"),
    MANUAL("Manual (coordenação)");

    private final String label;

    HolidaySyncTrigger(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

}
