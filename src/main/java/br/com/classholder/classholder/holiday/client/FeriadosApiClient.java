package br.com.classholder.classholder.holiday.client;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import br.com.classholder.classholder.holiday.domain.HolidayType;

@Component
public class FeriadosApiClient {

    public static final String SOURCE_NAME = "FeriadosAPI";

    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int PAGE_LIMIT = 100;

    private final RestClient restClient;
    private final String ibgeCode;
    private final boolean tokenConfigured;

    @Autowired
    public FeriadosApiClient(@Value("${app.feriados.base-url}") String baseUrl,
            @Value("${app.feriados.token}") String token,
            @Value("${app.feriados.codigo-ibge}") String ibgeCode) {
        this(RestClient.builder().requestFactory(requestFactoryWithTimeouts()), baseUrl, token, ibgeCode);
    }

    FeriadosApiClient(RestClient.Builder builder, String baseUrl, String token, String ibgeCode) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        this.ibgeCode = ibgeCode;
        this.tokenConfigured = !token.isBlank();
    }

    public List<FetchedHoliday> fetchHolidays(int year) {
        if (!tokenConfigured) {
            throw new IllegalStateException("Token da FeriadosAPI não configurado (variável FERIADOS_API_TOKEN)");
        }

        List<FetchedHoliday> holidays = new ArrayList<>();
        int totalPages = 1;
        for (int page = 1; page <= totalPages; page++) {
            HolidaysPage response = fetchPage(year, page);
            if (response == null || response.feriados() == null) {
                throw new IllegalStateException("A FeriadosAPI retornou uma resposta vazia");
            }
            response.feriados().forEach(item -> holidays.add(toFetchedHoliday(item)));
            if (response.meta() != null && response.meta().totalPages() != null) {
                totalPages = response.meta().totalPages();
            }
        }
        return holidays;
    }

    private HolidaysPage fetchPage(int year, int page) {
        return restClient.get()
                .uri(uri -> uri.path("/feriados/cidade/{ibge}")
                        .queryParam("ano", year)
                        .queryParam("facultativos", true)
                        .queryParam("page", page)
                        .queryParam("limit", PAGE_LIMIT)
                        .build(ibgeCode))
                .retrieve()
                .body(HolidaysPage.class);
    }

    private static FetchedHoliday toFetchedHoliday(HolidayItem item) {
        return new FetchedHoliday(LocalDate.parse(item.data(), API_DATE_FORMAT), item.nome(),
                HolidayType.valueOf(item.tipo()));
    }

    private static SimpleClientHttpRequestFactory requestFactoryWithTimeouts() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return requestFactory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidaysPage(List<HolidayItem> feriados, Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HolidayItem(String data, String nome, String tipo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(@JsonProperty("total_pages") Integer totalPages) {
    }

}
