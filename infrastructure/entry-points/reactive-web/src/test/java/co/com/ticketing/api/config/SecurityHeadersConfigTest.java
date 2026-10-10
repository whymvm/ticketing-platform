package co.com.ticketing.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

class SecurityHeadersConfigTest {

    private final WebTestClient client = WebTestClient
            .bindToRouterFunction(RouterFunctions.route(GET("/ping"), request -> ServerResponse.ok().build()))
            .webFilter(new SecurityHeadersConfig())
            .build();

    @Test
    void addsSecurityHeadersToEveryResponse() {
        client.get().uri("/ping")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
                .expectHeader().exists("Content-Security-Policy")
                .expectHeader().exists("Strict-Transport-Security")
                .expectHeader().doesNotExist("Server");
    }
}
