package br.com.classholder.classholder.shared;

import java.util.List;
import java.util.Map;

import org.springframework.web.util.UriComponentsBuilder;

public record ListPage<T>(List<T> rows, long total, int page, int totalPages, String baseQuery, boolean filtered) {

    public static final int DEFAULT_SIZE = 20;

    public static <T> ListPage<T> of(List<T> matched, int requestedPage, int size, Map<String, ?> params) {
        int totalPages = Math.max(1, (int) Math.ceil(matched.size() / (double) size));
        int page = Math.min(Math.max(requestedPage, 1), totalPages);
        int start = Math.min((page - 1) * size, matched.size());
        int end = Math.min(start + size, matched.size());
        return new ListPage<>(List.copyOf(matched.subList(start, end)), matched.size(), page, totalPages,
                baseQuery(params), hasValues(params));
    }

    public static <T> ListPage<T> of(List<T> matched, int requestedPage, Map<String, ?> params) {
        return of(matched, requestedPage, DEFAULT_SIZE, params);
    }

    private static boolean hasValues(Map<String, ?> params) {
        return params.values().stream().anyMatch(ListPage::hasValue);
    }

    private static boolean hasValue(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private static String baseQuery(Map<String, ?> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach((name, value) -> {
            if (hasValue(value)) {
                builder.queryParam(name, value);
            }
        });
        String query = builder.encode().build().getQuery();
        return query == null ? "" : query + "&";
    }

}
