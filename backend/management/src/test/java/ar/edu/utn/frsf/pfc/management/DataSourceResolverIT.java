package ar.edu.utn.frsf.pfc.management;

import java.sql.SQLException;
import java.time.Instant;

import ar.edu.utn.frsf.pfc.management.catalog.DataSourceResolver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DataSourceResolverIT {

    private static final ManagementPostgres POSTGRES = new ManagementPostgres();
    private static final Instant START = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant CUTOVER = Instant.parse("2026-01-15T12:05:00Z");

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start();
        var dataSource = POSTGRES.bean(JdbcTemplate.class).getDataSource();
        var fixture = new ResourceDatabasePopulator(new ClassPathResource("temporal-source-fixture.sql"));
        fixture.execute(dataSource);
    }

    @AfterAll
    static void stop() {
        POSTGRES.close();
    }

    @Test
    void resolvesTheEffectiveSourceAtAnAdjacentBoundaryAndForLateData() {
        var resolver = POSTGRES.bean(DataSourceResolver.class);
        var lateOccurrence = Instant.parse("2026-01-15T12:04:30Z");
        var lateReceipt = Instant.parse("2026-01-15T12:12:00Z");

        assertThat(resolver.findSourceId("000123", "engine.rpm", START.minusNanos(1))).isEmpty();
        assertThat(resolver.findSourceId("000123", "engine.rpm", START)).contains("rpm-a");
        assertThat(resolver.findSourceId("000123", "engine.rpm", CUTOVER.minusNanos(1))).contains("rpm-a");
        assertThat(resolver.findSourceId("000123", "engine.rpm", CUTOVER)).contains("rpm-b");
        assertThat(resolver.findSourceId("000123", "engine.rpm", CUTOVER.plusNanos(1))).contains("rpm-b");
        assertThat(resolver.findSourceId("000123", "engine.rpm", lateOccurrence)).contains("rpm-a");
        assertThat(resolver.findSourceId("000123", "engine.rpm", lateReceipt)).contains("rpm-b");
    }

    @Test
    void rejectsOverlapEvenWhenSourcesDifferAndRejectsEmptyIntervals() {
        assertInvalidBinding("23P01", """
                INSERT INTO data_source_binding VALUES (
                    '000123', 'embolsadora', 'engine.rpm', 'rpm-a',
                    '2026-01-15T12:06:00Z', '2026-01-15T12:07:00Z'
                )
                """);
        assertInvalidBinding("23514", """
                INSERT INTO data_source_binding VALUES (
                    '000123', 'embolsadora', 'engine.rpm', 'rpm-a',
                    '2026-01-15T11:00:00Z', '2026-01-15T11:00:00Z'
                )
                """);
    }

    @Test
    void rejectsSourcesFromOtherInstancesAndDefinitionsFromOtherTypes() {
        assertInvalidBinding("23503", """
                INSERT INTO data_source_binding VALUES (
                    '000123', 'embolsadora', 'engine.rpm', 'rpm-other',
                    '2026-01-15T11:00:00Z', '2026-01-15T12:00:00Z'
                )
                """);
        assertInvalidBinding("23503", """
                INSERT INTO data_source_binding VALUES (
                    '000123', 'other-type', 'engine.rpm', 'rpm-a',
                    '2026-01-15T11:00:00Z', '2026-01-15T12:00:00Z'
                )
                """);
    }

    private void assertInvalidBinding(String sqlState, String sql) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> POSTGRES.jdbc().sql(sql).update())
                .satisfies(error -> assertThat(error.getMostSpecificCause()).isInstanceOfSatisfying(
                        SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState)));
    }
}
