package co.com.ticketing.dynamodb.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * DynamoDB settings. The endpoint is only set when using an emulator such as LocalStack.
 */
@Validated
@ConfigurationProperties(prefix = DynamoDBProperties.PREFIX)
public record DynamoDBProperties(@NotBlank String region, String endpoint, @Valid @NotNull Tables tables) {

    public static final String PREFIX = "aws.dynamodb";

    public record Tables(@NotBlank String events, @NotBlank String orders, @NotBlank String orderHistory) {
    }
}
