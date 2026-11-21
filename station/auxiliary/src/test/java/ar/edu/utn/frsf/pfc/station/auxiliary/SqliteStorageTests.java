package ar.edu.utn.frsf.pfc.station.auxiliary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteStorageTests {

    @TempDir
    Path directory;

    @Test
    void initializesSqliteInConfiguredDirectory() throws Exception {
        var database = directory.resolve("station local #1.sqlite");

        try (var context = application(database).run()) {
            assertThat(Files.readAllBytes(database)).startsWith(
                    "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII));
        }
    }

    @Test
    void failsStartupWhenExistingDatabaseIsInvalid() throws Exception {
        var database = Files.writeString(directory.resolve("station.sqlite"), "invalid SQLite data");

        assertThatThrownBy(() -> application(database).run().close())
                .hasRootCauseInstanceOf(SQLException.class);
        assertThat(Files.readString(database)).isEqualTo("invalid SQLite data");
    }

    @Test
    void reportsDownWithoutRecreatingMissingDatabase() throws Exception {
        var database = directory.resolve("station.sqlite");

        try (var context = application(database).run()) {
            var indicator = context.getBean(SqliteStorageHealthIndicator.class);
            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

            Files.delete(database);
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(database).doesNotExist();
        }
    }

    private SpringApplicationBuilder application(Path database) {
        return new SpringApplicationBuilder(StationAuxiliaryApplication.class)
                .web(WebApplicationType.NONE)
                .properties("station.storage.sqlite-path=" + database);
    }
}
