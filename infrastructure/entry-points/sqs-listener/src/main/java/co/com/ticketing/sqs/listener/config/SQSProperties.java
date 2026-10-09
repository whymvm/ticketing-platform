package co.com.ticketing.sqs.listener.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
