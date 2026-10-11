package co.com.ticketing.scheduler;

import co.com.ticketing.usecase.releaseexpiredreservations.ReleaseExpiredReservationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.logging.Level;

/**
 * Periodically puts the tickets of expired reservations back in the inventory. Every instance of the
 * application runs it: the conditional writes make sure each reservation is released only once.
 */
@Log
@Component
@RequiredArgsConstructor
public class ExpiredReservationsScheduler {

    static final String DELAY = "${entrypoint.scheduler.expired-reservations-delay}";

    private final ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase;

    // Spring waits for the returned Mono before counting the delay, so two runs never overlap
    @Scheduled(fixedDelayString = DELAY, initialDelayString = DELAY)
    public Mono<Void> releaseExpiredReservations() {
        return releaseExpiredReservationsUseCase.run()
                .count()
                .filter(released -> released > 0)
                .doOnNext(released -> log.info(() -> "Released " + released + " expired reservations"))
                .doOnError(error -> log.log(Level.SEVERE, "Could not release expired reservations", error))
                .onErrorComplete()
                .then();
    }
}
