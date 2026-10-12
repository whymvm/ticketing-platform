package co.com.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Many customers buy tickets of the same event at the same moment, against the whole application: the REST API,
 * the SQS queue, the consumer and DynamoDB with its conditional writes, all real (LocalStack). No mock takes part.
 * The event never sells more tickets than it has (RF-5).
 */
@SpringBootTest(classes = MainApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Several pollers consume the queue at the same time, as several instances of the consumer would
                "entrypoint.sqs.numberOfThreads=4",
                // Short polls, so the application stops quickly when the test finishes
                "entrypoint.sqs.waitTimeSeconds=1"
        })
class ConcurrencyIntegrationTest {

    private static final int CAPACITY = 10;
    private static final int BUYERS = 50;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(60);

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        LocalInfrastructure.register(registry);
    }

    @Test
    void neverSellsMoreTicketsThanTheEventHasWhenManyCustomersBuyAtTheSameTime() {
        WebClient api = WebClient.create("http://localhost:" + port + "/api/v1");
        String eventId = createEvent(api);

        // All the requests are in flight at the same time
        List<Placed> placed = Flux.range(1, BUYERS)
                .flatMap(buyer -> api.post().uri("/orders")
                        .header("X-Customer-Id", "customer-" + buyer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("eventId", eventId, "quantity", 1))
                        .exchangeToMono(response -> response.bodyToMono(Order.class)
                                .map(order -> new Placed(response.statusCode(), order))), BUYERS)
                .collectList()
                .block();
        assertThat(placed).allSatisfy(order -> assertThat(order.status().value()).isEqualTo(202));

        // The consumer processes the orders asynchronously: waits until none of them is still queued
        List<Order> orders = await().atMost(PROCESSING_TIMEOUT)
                .until(() -> Flux.fromIterable(placed)
                                .flatMap(order -> api.get().uri("/orders/{id}", order.order().id())
                                        .header("X-Customer-Id", order.order().customerId())
                                        .retrieve()
                                        .bodyToMono(Order.class))
                                .collectList()
                                .block(),
                        current -> current.stream().noneMatch(order -> order.status().equals("QUEUED")));

        assertThat(orders).filteredOn(order -> order.status().equals("RESERVED")).hasSize(CAPACITY);
        assertThat(orders).filteredOn(order -> order.status().equals("REJECTED"))
                .hasSize(BUYERS - CAPACITY)
                .allSatisfy(order -> assertThat(order.rejectionReason()).isEqualTo("SOLD_OUT"));
        assertThat(api.get().uri("/events/{id}/availability", eventId).retrieve().bodyToMono(Availability.class)
                .block()).isEqualTo(new Availability(CAPACITY, 0, CAPACITY, 0, 0, 0));
    }

    private static String createEvent(WebClient api) {
        Instant startsAt = Instant.now().plus(Duration.ofDays(30));
        return api.post().uri("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Concurrency test", "venue", "Arena", "startsAt", startsAt.toString(),
                        "finishesAt", startsAt.plus(Duration.ofHours(3)).toString(), "capacity", CAPACITY))
                .retrieve()
                .bodyToMono(Order.class)
                .map(Order::id)
                .block();
    }

    record Order(String id, String customerId, String status, String rejectionReason) {
    }

    record Placed(HttpStatusCode status, Order order) {
    }

    record Availability(int capacity, int available, int reserved, int pendingConfirmation, int sold,
                        int complimentary) {
    }
}
