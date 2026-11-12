package br.com.classholder.classholder.holiday.domain;

public enum HolidayType {

    NACIONAL("feriado nacional"),
    ESTADUAL("feriado estadual"),
    MUNICIPAL("feriado municipal"),
    FACULTATIVO("ponto facultativo");

    private final String label;

    HolidayType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

}
