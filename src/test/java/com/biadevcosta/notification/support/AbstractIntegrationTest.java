package com.biadevcosta.notification.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared singleton MySQL + RabbitMQ containers, started once for the test run and left to Ryuk to
 * reap. When no Docker daemon is reachable the whole integration test is skipped (assumption
 * failure) instead of failing the build.
 */
public abstract class AbstractIntegrationTest {

    private static final boolean DOCKER_AVAILABLE = isDockerAvailable();

    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    protected static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    static {
        if (DOCKER_AVAILABLE) {
            MYSQL.start();
            RABBIT.start();
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DOCKER_AVAILABLE, "Docker is not available - skipping integration test");
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    }
}
