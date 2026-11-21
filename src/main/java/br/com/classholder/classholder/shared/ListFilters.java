package br.com.classholder.classholder.shared;

import java.text.Normalizer;
import java.util.Locale;

public final class ListFilters {

    private ListFilters() {
    }

    public static final int MAX_TERM_LENGTH = 100;

    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static String term(String value) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.length() <= MAX_TERM_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_TERM_LENGTH).strip();
    }

    public static boolean contains(String text, String term) {
        if (term == null || term.isBlank()) {
            return true;
        }
        return text != null && normalize(text).contains(normalize(term));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

}
