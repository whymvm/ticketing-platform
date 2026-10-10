package co.com.ticketing.sqs.sender.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SQSSenderConfigTest {

    private static final SQSSenderProperties.Queues QUEUES = new SQSSenderProperties.Queues("http://queue");

    @Mock
    private MetricPublisher publisher;

    private final SQSSenderConfig config = new SQSSenderConfig();

    @Test
    void usesTheConfiguredEndpoint() {
        SQSSenderProperties properties = new SQSSenderProperties("us-east-1", "http://localstack:4566", QUEUES);

        try (SqsAsyncClient client = config.sqsSenderClient(properties, publisher)) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("http://localstack:4566"));
        }
    }

    @Test
    void usesTheDefaultAwsEndpointWhenNoneIsConfigured() {
        SQSSenderProperties properties = new SQSSenderProperties("us-east-1", null, QUEUES);

        try (SqsAsyncClient client = config.sqsSenderClient(properties, publisher)) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }

    @Test
    void ignoresABlankEndpoint() {
        SQSSenderProperties properties = new SQSSenderProperties("us-east-1", " ", QUEUES);

        try (SqsAsyncClient client = config.sqsSenderClient(properties, publisher)) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }
}
