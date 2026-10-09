package co.com.ticketing.sqs.sender.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = SQSSenderProperties.PREFIX)
public record SQSSenderProperties(@NotBlank String region, String endpoint, @Valid @NotNull Queues queues) {

    public static final String PREFIX = "adapter.sqs";

    public record Queues(@NotBlank String reservations, @NotBlank String payments) {
    }
}
