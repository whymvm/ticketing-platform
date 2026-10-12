package co.com.ticketing.metrics.aws;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class MicrometerMetricPublisher implements MetricPublisher {
    private final ExecutorService service = Executors.newFixedThreadPool(10);
    private final MeterRegistry registry;

    @Override
    public void publish(MetricCollection metricCollection) {
        service.submit(() -> {
            List<Tag> tags = buildTags(metricCollection);
            metricCollection.stream().forEach(metricRecord -> {
                String name = metricRecord.metric().name();
                if (metricRecord.value() instanceof Duration duration) {
                    registry.timer(name, tags).record(duration);
                } else if (metricRecord.value() instanceof Integer count) {
                    registry.counter(name, tags).increment(count);
                }
            });
        });
    }

    @Override
    public void close() {
        // Nothing to close here: the DynamoDB and both SQS clients share this publisher, so closing one client must
        // not stop the metrics of the others
    }

    private List<Tag> buildTags(MetricCollection metricCollection) {
        return metricCollection.stream()
                .filter(metricRecord -> metricRecord.value() instanceof String || metricRecord.value() instanceof Boolean)
                .map(metricRecord -> Tag.of(metricRecord.metric().name(), metricRecord.value().toString()))
                .toList();
    }
}
