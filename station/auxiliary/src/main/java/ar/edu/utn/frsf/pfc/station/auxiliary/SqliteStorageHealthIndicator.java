package ar.edu.utn.frsf.pfc.station.auxiliary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("sqlite")
@ConditionalOnProperty(name = "station.storage.sqlite-path")
class SqliteStorageHealthIndicator implements HealthIndicator {

    private final Path database;
    private final String jdbcUrl;

    SqliteStorageHealthIndicator(@Value("${station.storage.sqlite-path}") String sqlitePath) {
        database = Path.of(sqlitePath).toAbsolutePath().normalize();
        jdbcUrl = "jdbc:sqlite:" + database.toUri().toASCIIString();
    }

    @PostConstruct
    void initialize() throws IOException, SQLException {
        try (var connection = DriverManager.getConnection(jdbcUrl + "?mode=rwc");
                var statement = connection.createStatement()) {
            if (Files.size(database) == 0) {
                // An empty SQLite connection may leave a zero-byte file until its first write.
                statement.execute("VACUUM");
            }
        }
        checkDatabase();
    }

    @Override
    public Health health() {
        try {
            checkDatabase();
            return Health.up().build();
        } catch (IOException | SQLException exception) {
            return Health.down().build();
        }
    }

    private void checkDatabase() throws IOException, SQLException {
        if (!Files.isRegularFile(database) || !Files.isWritable(database)
                || !Files.isWritable(database.getParent())) {
            throw new IOException("SQLite storage requires a file in a writable directory");
        }

        if (Files.size(database) == 0) {
            throw new IOException("SQLite storage is empty");
        }

        try (var connection = DriverManager.getConnection(jdbcUrl + "?mode=ro");
                var statement = connection.createStatement();
                var result = statement.executeQuery("PRAGMA schema_version")) {
            if (!result.next()) {
                throw new SQLException("SQLite did not return its schema version");
            }
        }
    }
}
