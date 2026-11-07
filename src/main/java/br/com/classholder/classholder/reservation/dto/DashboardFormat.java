package br.com.classholder.classholder.reservation.dto;

import java.util.Locale;

public final class DashboardFormat {

    public static final String NO_DATA = "Sem dados";

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private DashboardFormat() {
    }

    public static String duration(long minutes) {
        return (minutes / 60) + "h " + (minutes % 60) + "min";
    }

    public static String percent(Double value) {
        return value == null ? NO_DATA : String.format(PT_BR, "%.1f%%", value);
    }

    public static String ratioPercent(long part, long total) {
        return total == 0 ? NO_DATA : percent(part * 100.0 / total);
    }

}
