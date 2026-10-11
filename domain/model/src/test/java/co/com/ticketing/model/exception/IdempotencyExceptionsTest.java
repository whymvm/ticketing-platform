package co.com.ticketing.model.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyExceptionsTest {

    @Test
    void reusedKeyExplainsTheProblemWithoutStackTrace() {
        IdempotencyKeyReusedException error = new IdempotencyKeyReusedException();

        assertThat(error).hasMessage("the Idempotency-Key was already used with a different request");
        assertThat(error.getStackTrace()).isEmpty();
    }

    @Test
    void duplicateRequestNamesTheKey() {
        DuplicateRequestException error = new DuplicateRequestException("c-1#key-0001");

        assertThat(error).hasMessage("another request with the key c-1#key-0001 was processed at the same time");
        assertThat(error.getStackTrace()).isEmpty();
    }

    @Test
    void missingAndInvalidKeysCarryNoStackTrace() {
        assertThat(new MissingIdempotencyKeyException().getStackTrace()).isEmpty();
        assertThat(new InvalidIdempotencyKeyException(8, 64).getStackTrace()).isEmpty();
    }
}
