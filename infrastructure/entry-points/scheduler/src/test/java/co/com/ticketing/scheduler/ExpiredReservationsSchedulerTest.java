package co.com.ticketing.scheduler;

import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.usecase.releaseexpiredreservations.ReleaseExpiredReservationsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredReservationsSchedulerTest {

    @Mock
    private ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase;

    @InjectMocks
    private ExpiredReservationsScheduler scheduler;

    @Test
    void releasesTheExpiredReservations() {
        ExpiredOrder expired = new ExpiredOrder(
                new OrderDetails("o-1", "e-1", "c-1", 2, Instant.parse("2026-10-10T12:00:00Z")));
        when(releaseExpiredReservationsUseCase.run()).thenReturn(Flux.just(expired));

        StepVerifier.create(scheduler.releaseExpiredReservations()).verifyComplete();
    }

    @Test
    void completesWhenThereIsNothingToRelease() {
        when(releaseExpiredReservationsUseCase.run()).thenReturn(Flux.empty());

        StepVerifier.create(scheduler.releaseExpiredReservations()).verifyComplete();
    }

    @Test
    void keepsTheScheduleAliveWhenARunFails() {
        when(releaseExpiredReservationsUseCase.run()).thenReturn(Flux.error(new IllegalStateException("dynamo down")));

        StepVerifier.create(scheduler.releaseExpiredReservations()).verifyComplete();
    }

    @Test
    void runsWithAConfigurableDelay() throws NoSuchMethodException {
        Scheduled scheduled = ExpiredReservationsScheduler.class
                .getMethod("releaseExpiredReservations").getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${entrypoint.scheduler.expired-reservations-delay}");
    }
}
