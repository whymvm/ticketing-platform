package co.com.ticketing.sqs.sender.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

import java.net.URI;
import java.util.Optional;

@Configuration
public class SQSSenderConfig {

    @Bean
    public SqsAsyncClient sqsSenderClient(SQSSenderProperties properties, MetricPublisher publisher) {
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(Region.of(properties.region()))
                .overrideConfiguration(o -> o.addMetricPublisher(publisher));
        Optional.ofNullable(properties.endpoint())
                .filter(endpoint -> !endpoint.isBlank())
                .map(URI::create)
                .ifPresent(builder::endpointOverride);
        return builder.build();
    }
}
