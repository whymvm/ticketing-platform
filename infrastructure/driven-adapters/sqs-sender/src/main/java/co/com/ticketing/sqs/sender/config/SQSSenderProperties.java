package co.com.ticketing.sqs.sender.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SQS settings. The endpoint is only set when using an emulator such as LocalStack.
 */
@Validated
@ConfigurationProperties(prefix = SQSSenderProperties.PREFIX)
public record SQSSenderProperties(@NotBlank String region, String endpoint, @Valid @NotNull Queues queues) {

    public static final String PREFIX = "adapter.sqs";

    public record Queues(@NotBlank String reservations) {
    }
}
