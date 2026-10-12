package co.com.ticketing.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects the administrative operations with a shared key, read from the environment (in AWS it would come from
 * Secrets Manager). It is a minimal protection until there is real authentication: when no key is configured,
 * every administrative request is denied.
 */
@Component
public class AdminAccess {

    private final byte[] adminKey;

    public AdminAccess(@Value("${security.admin-api-key:}") String adminKey) {
        this.adminKey = adminKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Completes when the request carries the admin key, and emits AdminAccessDeniedException otherwise.
     */
    public Mono<Void> check(ServerRequest request) {
        return Mono.justOrEmpty(request.headers().firstHeader(ApiHeaders.ADMIN_KEY))
                .filter(this::matches)
                .switchIfEmpty(Mono.error(AdminAccessDeniedException::new))
                .then();
    }

    // Constant-time comparison, so the response time does not reveal how much of a guessed key is right
    private boolean matches(String candidate) {
        return adminKey.length > 0
                && MessageDigest.isEqual(adminKey, candidate.getBytes(StandardCharsets.UTF_8));
    }
}
