package co.com.ticketing;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;

/**
 * The infrastructure of the local environment, started once for all the integration tests: LocalStack (DynamoDB and
 * SQS) in Docker, with its tables, index and queues created by the same Terraform that docker-compose applies.
 * Nothing is created by hand, so the tests also prove that the Terraform matches what the application expects.
 */
final class LocalInfrastructure {

    private static final String LOCALSTACK_IMAGE = "localstack/localstack:4.14.0";
    private static final String TERRAFORM_IMAGE = "hashicorp/terraform:1.16.3";
    private static final String LOCALSTACK_ALIAS = "localstack";
    private static final String NAME_PREFIX = "local-ticketing-";
    private static final String ACCOUNT_ID = "000000000000";
    // Docker volume that keeps the AWS provider between runs, so Terraform downloads it only the first time
    private static final String PLUGIN_CACHE_VOLUME = "ticketing-integration-terraform-plugins";
    private static final Duration TERRAFORM_TIMEOUT = Duration.ofMinutes(5);

    private static final Network NETWORK = Network.newNetwork();
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse(LOCALSTACK_IMAGE))
            .withServices("dynamodb", "sqs")
            .withNetwork(NETWORK)
            .withNetworkAliases(LOCALSTACK_ALIAS);

    static {
        LOCALSTACK.start();
        applyTerraform();
    }

    private LocalInfrastructure() {
    }

    /**
     * Points the application to LocalStack and to the resources that Terraform created.
     */
    static void register(DynamicPropertyRegistry registry) {
        String endpoint = LOCALSTACK.getEndpoint().toString();
        String reservationsQueue = endpoint + "/" + ACCOUNT_ID + "/" + NAME_PREFIX + "reservations";
        registry.add("aws.region", LOCALSTACK::getRegion);
        registry.add("aws.endpoint", () -> endpoint);
        registry.add("aws.dynamodb.tables.events", () -> NAME_PREFIX + "events");
        registry.add("aws.dynamodb.tables.orders", () -> NAME_PREFIX + "orders");
        registry.add("aws.dynamodb.tables.orderHistory", () -> NAME_PREFIX + "order-history");
        registry.add("aws.dynamodb.tables.idempotency", () -> NAME_PREFIX + "idempotency");
        registry.add("adapter.sqs.queues.reservations", () -> reservationsQueue);
        registry.add("entrypoint.sqs.queueUrl", () -> reservationsQueue);
    }

    // Runs `terraform apply` in a container of the same network, and waits until it finishes successfully
    private static void applyTerraform() {
        Path terraformDir = Path.of(System.getProperty("ticketing.terraform-dir"));
        try (GenericContainer<?> terraform = new GenericContainer<>(DockerImageName.parse(TERRAFORM_IMAGE))
                .withNetwork(NETWORK)
                .withCopyFileToContainer(MountableFile.forHostPath(terraformDir), "/infra")
                .withWorkingDirectory("/infra/envs/local")
                .withEnv("TF_VAR_localstack_endpoint", "http://" + LOCALSTACK_ALIAS + ":4566")
                .withEnv("TF_DATA_DIR", "/tmp/.terraform")
                .withEnv("TF_PLUGIN_CACHE_DIR", "/plugin-cache")
                .withCreateContainerCmdModifier(command -> {
                    command.withEntrypoint("/bin/sh");
                    command.getHostConfig().withBinds(new Bind(PLUGIN_CACHE_VOLUME, new Volume("/plugin-cache")));
                })
                // Two arguments: withCommand(String) would split the script on its spaces
                .withCommand("-c", "terraform init -input=false -backend-config=path=/tmp/terraform.tfstate"
                        + " && terraform apply -input=false -auto-approve")
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("terraform")))
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(TERRAFORM_TIMEOUT))) {
            terraform.start();
        }
    }
}
