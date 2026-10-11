package co.com.ticketing.sqs.listener.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the queue listener. The visibility timeout is not set here: the queue owns it.
 */
@Validated
@ConfigurationProperties(prefix = SQSProperties.PREFIX)
public record SQSProperties(
        @NotBlank String region,
        String endpoint,
        @NotBlank String queueUrl,
        int waitTimeSeconds,
        int maxNumberOfMessages,
        int numberOfThreads) {

    public static final String PREFIX = "entrypoint.sqs";
}
