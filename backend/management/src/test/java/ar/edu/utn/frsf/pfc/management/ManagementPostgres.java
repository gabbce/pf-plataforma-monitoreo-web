package ar.edu.utn.frsf.pfc.management;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

final class ManagementPostgres implements AutoCloseable {

    private static final String IMAGE =
            "postgres:19.0-trixie@sha256:bb6e70add742cd868fb62cb3b2f67d1dd2bdd2b65a521e778420748753687c60";

    private final String password = UUID.randomUUID().toString();
    private final String adminPassword = UUID.randomUUID().toString();
    private final GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withEnv("POSTGRES_DB", "management")
            .withEnv("POSTGRES_INITDB_ARGS", "--auth-host=scram-sha-256")
            .withEnv("POSTGRES_PASSWORD", adminPassword)
            .withTmpFs(Map.of("/var/lib/postgresql", "rw"))
            .withExposedPorts(5432)
            .withCopyFileToContainer(MountableFile.forHostPath("../../infrastructure/platform/provision-management.sh"),
                    "/provision-management.sh")
            .withCopyToContainer(Transferable.of(adminPassword, 0444), "/run/secrets/postgres_password")
            .withCopyToContainer(Transferable.of(password, 0444), "/run/secrets/management_db_password")
            .waitingFor(Wait.forSuccessfulCommand("pg_isready -q -h 127.0.0.1 -U postgres -d management"));

    private ConfigurableApplicationContext application;

    void start(Class<?>... additionalSources) throws Exception {
        startDatabase();
        startApplication(additionalSources);
    }

    void startDatabase() throws Exception {
        postgres.start();
        provision();
    }

    void startApplication(Class<?>... additionalSources) {
        application = new SpringApplicationBuilder(ManagementApplication.class).sources(additionalSources).run(
                "--server.port=0",
                "--server.address=127.0.0.1",
                "--spring.datasource.url=" + jdbcUrl(),
                "--spring.datasource.username=management_app",
                "--spring.datasource.password=" + password);
    }

    void provision() throws Exception {
        var result = postgres.execInContainer("env", "PGHOST=127.0.0.1", "/bin/sh", "/provision-management.sh");
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    }

    String jdbcUrl() {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/management";
    }

    String password() {
        return password;
    }

    <T> T bean(Class<T> type) {
        return application.getBean(type);
    }

    JdbcClient jdbc() {
        return bean(JdbcClient.class);
    }

    int port() {
        return application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
    }

    @Override
    public void close() {
        try {
            if (application != null) {
                application.close();
            }
        } finally {
            postgres.close();
        }
    }
}
