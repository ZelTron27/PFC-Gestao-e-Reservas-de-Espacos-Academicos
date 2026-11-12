package br.com.classholder.classholder.holiday.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import br.com.classholder.classholder.holiday.domain.HolidayType;

class FeriadosApiClientTest {

    private static final String BASE_URL = "https://api.teste/v1";
    private static final String CITY_URL = BASE_URL + "/feriados/cidade/3550308?ano=2026&facultativos=true&limit=100";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void convertsDatesAndTypesAndSendsBearerToken() {
        FeriadosApiClient client = new FeriadosApiClient(builder, BASE_URL, "token-teste", "3550308");
        server.expect(requestTo(pageUrl(1)))
                .andExpect(header("Authorization", "Bearer token-teste"))
                .andRespond(withSuccess("""
                        {"feriados":[
                          {"data":"25/01/2026","nome":"Aniversário da Cidade de São Paulo","tipo":"MUNICIPAL","uf":"SP"},
                          {"data":"17/02/2026","nome":"Carnaval","tipo":"FACULTATIVO","bancario":true}
                        ],"meta":{"total":2,"page":1,"per_page":100,"total_pages":1}}
                        """, MediaType.APPLICATION_JSON));

        List<FetchedHoliday> holidays = client.fetchHolidays(2026);

        assertThat(holidays).containsExactly(
                new FetchedHoliday(LocalDate.of(2026, 1, 25), "Aniversário da Cidade de São Paulo",
                        HolidayType.MUNICIPAL),
                new FetchedHoliday(LocalDate.of(2026, 2, 17), "Carnaval", HolidayType.FACULTATIVO));
        server.verify();
    }

    @Test
    void readsEveryPage() {
        FeriadosApiClient client = new FeriadosApiClient(builder, BASE_URL, "token-teste", "3550308");
        server.expect(requestTo(pageUrl(1))).andRespond(withSuccess("""
                {"feriados":[{"data":"01/01/2026","nome":"Ano Novo","tipo":"NACIONAL"}],"meta":{"total_pages":2}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(pageUrl(2))).andRespond(withSuccess("""
                {"feriados":[{"data":"25/12/2026","nome":"Natal","tipo":"NACIONAL"}],"meta":{"total_pages":2}}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchHolidays(2026)).extracting(FetchedHoliday::name).containsExactly("Ano Novo", "Natal");
        server.verify();
    }

    @Test
    void propagatesUnauthorized() {
        FeriadosApiClient client = new FeriadosApiClient(builder, BASE_URL, "token-invalido", "3550308");
        server.expect(requestTo(pageUrl(1))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void failsWithoutCallingTheApiWhenTokenIsMissing() {
        FeriadosApiClient client = new FeriadosApiClient(builder, BASE_URL, "", "3550308");

        assertThatThrownBy(() -> client.fetchHolidays(2026))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FERIADOS_API_TOKEN");
        server.verify();
    }

    private static String pageUrl(int page) {
        return CITY_URL.replace("&limit", "&page=" + page + "&limit");
    }

}
