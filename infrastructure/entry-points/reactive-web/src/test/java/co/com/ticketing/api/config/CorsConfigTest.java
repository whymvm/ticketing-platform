package co.com.ticketing.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

class CorsConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    private final WebTestClient client = WebTestClient
            .bindToRouterFunction(RouterFunctions.route(POST("/ping"), request -> ServerResponse.ok().build()))
            .webFilter(new CorsConfig().corsWebFilter(List.of(ALLOWED_ORIGIN)))
            .configureClient()
            // CORS compares the request origin with the server origin, so the request needs a full URL
            .baseUrl("http://localhost:8080")
            .build();

    @Test
    void allowsPreflightFromAConfiguredOrigin() {
        client.options().uri("/ping")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void rejectsPreflightFromAnUnknownOrigin() {
        client.options().uri("/ping")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void rejectsMethodsThatAreNotAllowed() {
        client.options().uri("/ping")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                .exchange()
                .expectStatus().isForbidden();
    }
}
