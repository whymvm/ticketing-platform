package co.com.ticketing.sqs.listener.config;

import co.com.ticketing.sqs.listener.helper.SQSListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;
import software.amazon.awssdk.services.sqs.model.Message;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

@Configuration
public class SQSConfig {

    public static final String CLIENT = "sqsListenerClient";

    @Bean
    public SQSListener sqsListener(@Qualifier(CLIENT) SqsAsyncClient client, SQSProperties properties,
                                   Function<Message, Mono<Void>> fn, MeterRegistry meterRegistry) {
        return SQSListener.builder()
                .client(client)
                .properties(properties)
                .processor(fn)
                .meterRegistry(meterRegistry)
                .build()
                .start();
    }

    @Bean(CLIENT)
    public SqsAsyncClient sqsListenerClient(SQSProperties properties, MetricPublisher publisher) {
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
