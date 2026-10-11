package co.com.ticketing.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.test.StepVerifier;

class AdminAccessTest {

    @Test
    void letsARequestWithTheAdminKeyThrough() {
        StepVerifier.create(new AdminAccess("admin-key").check(requestWithKey("admin-key")))
                .verifyComplete();
    }

    @Test
    void deniesEveryRequestWhenNoKeyIsConfigured() {
        StepVerifier.create(new AdminAccess("").check(requestWithKey("")))
                .expectError(AdminAccessDeniedException.class)
                .verify();
    }

    private static ServerRequest requestWithKey(String adminKey) {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/events/e-1/complimentary")
                .header(ApiHeaders.ADMIN_KEY, adminKey)
                .build();
        return ServerRequest.create(MockServerWebExchange.from(request), HandlerStrategies.withDefaults().messageReaders());
    }
}
