package co.com.ticketing.dynamodb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DynamoDBConfigTest {

    private static final DynamoDBProperties.Tables TABLES = new DynamoDBProperties.Tables("events", "orders", "local-ticketing-order-history", "local-ticketing-idempotency");

    @Mock
    private MetricPublisher publisher;

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    private final DynamoDBConfig dynamoDBConfig = new DynamoDBConfig();

    @Test
    void usesTheConfiguredEndpoint() {
        DynamoDBProperties properties = new DynamoDBProperties("us-east-1", "http://localstack:4566", TABLES);

        try (DynamoDbAsyncClient client = dynamoDBConfig.amazonDynamoDB(properties, publisher)) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("http://localstack:4566"));
        }
    }

    @Test
    void usesTheDefaultAwsEndpointWhenNoneIsConfigured() {
        DynamoDBProperties properties = new DynamoDBProperties("us-east-1", null, TABLES);

        try (DynamoDbAsyncClient client = dynamoDBConfig.amazonDynamoDB(properties, publisher)) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }

    @Test
    void ignoresABlankEndpoint() {
        DynamoDBProperties properties = new DynamoDBProperties("us-east-1", " ", TABLES);

        try (DynamoDbAsyncClient client = dynamoDBConfig.amazonDynamoDB(properties, publisher)) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }

    @Test
    void createsTheEnhancedClient() {
        DynamoDbEnhancedAsyncClient result = dynamoDBConfig.getDynamoDbEnhancedAsyncClient(dynamoDbAsyncClient);

        assertThat(result).isNotNull();
    }
}
