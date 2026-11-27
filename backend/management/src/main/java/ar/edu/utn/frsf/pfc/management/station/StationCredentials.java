package ar.edu.utn.frsf.pfc.management.station;

import java.security.MessageDigest;
import java.sql.Types;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationCredentials {

    private final JdbcClient jdbc;

    public StationCredentials(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Outcome provision(String instanceId, String producerId, Set<StationOperation> operations, byte[] token) {
        requireIdentity(instanceId, producerId);
        if (operations == null || operations.isEmpty()) {
            throw new Rejected("At least one Station operation is required");
        }
        var requestedOperations = Set.copyOf(operations);
        if (!StationToken.isValid(token)) {
            throw new Rejected("A Station token must contain 64 lowercase hexadecimal characters");
        }
        var digest = StationToken.digest(token);

        if (jdbc.sql("SELECT instance_id FROM machine_instance WHERE instance_id = :instance FOR KEY SHARE")
                .param("instance", instanceId).query(String.class).optional().isEmpty()) {
            throw new Rejected("Unknown machine instance");
        }
        var created = jdbc.sql("""
                INSERT INTO station_credential (instance_id, producer_id, token_digest)
                VALUES (:instance, :producer, :digest)
                ON CONFLICT DO NOTHING RETURNING credential_id
                """)
                .param("instance", instanceId).param("producer", producerId).param("digest", digest, Types.BINARY)
                .query(Long.class).optional();
        if (created.isPresent()) {
            var id = created.get();
            for (var operation : requestedOperations) {
                jdbc.sql("""
                        INSERT INTO station_credential_operation (credential_id, operation) VALUES (:id, :operation)
                        """)
                        .param("id", id).param("operation", operation.name()).update();
            }
            audit(id, "p1-station-provision", "CREATE");
            return Outcome.CREATED;
        }

        var existing = lockCredential(instanceId, producerId).orElseThrow(StationCredentials::conflict);
        if (!MessageDigest.isEqual(existing.digest(), digest) || !operationsFor(existing.id()).equals(requestedOperations)) {
            throw conflict();
        }
        return existing.revoked() ? Outcome.ALREADY_REVOKED : Outcome.ALREADY_EXISTS;
    }

    @Transactional
    public Outcome revoke(String instanceId, String producerId) {
        requireIdentity(instanceId, producerId);
        var existing = lockCredential(instanceId, producerId)
                .orElseThrow(() -> new Rejected("Unknown Station credential"));
        if (existing.revoked()) {
            return Outcome.ALREADY_REVOKED;
        }
        jdbc.sql("""
                UPDATE station_credential SET revoked_at = GREATEST(statement_timestamp(), created_at)
                WHERE credential_id = :id
                """)
                .param("id", existing.id()).update();
        audit(existing.id(), "p1-station-revoke", "REVOKE");
        return Outcome.REVOKED;
    }

    public boolean authorizes(String instanceId, String producerId, StationOperation operation, byte[] token) {
        return authorizedCredential(instanceId, producerId, operation, token, false).isPresent();
    }

    @Transactional
    public boolean authorizeAndRecordContact(String instanceId, String producerId, byte[] token) {
        var credentialId = authorizedCredential(instanceId, producerId, StationOperation.RECORDS_WRITE, token, true);
        if (credentialId.isEmpty()) {
            return false;
        }
        var updated = jdbc.sql("""
                UPDATE station_credential
                SET last_authenticated_contact_at = GREATEST(last_authenticated_contact_at, clock_timestamp())
                WHERE credential_id = :id
                """)
                .param("id", credentialId.get()).update();
        if (updated != 1) {
            throw new IllegalStateException("Station contact update failed");
        }
        return true;
    }

    private Optional<Long> authorizedCredential(String instanceId, String producerId, StationOperation operation,
            byte[] token, boolean lock) {
        if (!isIdentity(instanceId) || !isIdentity(producerId) || operation == null || !StationToken.isValid(token)) {
            return Optional.empty();
        }
        var digest = StationToken.digest(token);
        // The contact check locks the same row used by revoke until the contact commits.
        var locking = lock ? " FOR UPDATE OF credential" : "";
        return jdbc.sql("""
                SELECT credential.credential_id, credential.token_digest
                FROM station_credential credential
                JOIN station_credential_operation grant_row USING (credential_id)
                WHERE credential.instance_id = :instance AND credential.producer_id = :producer
                  AND credential.revoked_at IS NULL AND grant_row.operation = :operation
                """ + locking)
                .param("instance", instanceId).param("producer", producerId).param("operation", operation.name())
                .query((row, index) -> new StoredVerifier(row.getLong(1), row.getBytes(2))).optional()
                .filter(stored -> MessageDigest.isEqual(stored.digest(), digest))
                .map(StoredVerifier::id);
    }

    private Optional<StoredCredential> lockCredential(String instanceId, String producerId) {
        // Serialize checks and state changes for the same credential across processes.
        return jdbc.sql("""
                SELECT credential_id, token_digest, revoked_at IS NOT NULL
                FROM station_credential WHERE instance_id = :instance AND producer_id = :producer FOR UPDATE
                """)
                .param("instance", instanceId).param("producer", producerId)
                .query((row, index) -> new StoredCredential(row.getLong(1), row.getBytes(2), row.getBoolean(3)))
                .optional();
    }

    private Set<StationOperation> operationsFor(long id) {
        return Set.copyOf(jdbc.sql("SELECT operation FROM station_credential_operation WHERE credential_id = :id")
                .param("id", id).query((row, index) -> StationOperation.valueOf(row.getString(1))).list());
    }

    private void audit(long id, String actor, String action) {
        jdbc.sql("""
                INSERT INTO audit_record (actor_type, actor_reference, action, element_type, element_id, occurred_at)
                VALUES ('SISTEMA', :actor, :action, 'station_credential', :id, statement_timestamp())
                """)
                .param("actor", actor).param("action", action).param("id", Long.toString(id)).update();
    }

    static void requireIdentity(String instanceId, String producerId) {
        if (!isIdentity(instanceId) || !isIdentity(producerId)) {
            throw new Rejected("Machine instance and producer identifiers are required");
        }
    }

    private static boolean isIdentity(String text) {
        return text != null && !text.isBlank() && text.indexOf('\0') < 0;
    }

    private static Rejected conflict() {
        return new Rejected("Conflicting Station credential");
    }

    public enum Outcome {
        CREATED,
        ALREADY_EXISTS,
        REVOKED,
        ALREADY_REVOKED
    }

    static class Rejected extends IllegalArgumentException {

        Rejected(String message) {
            super(message);
        }
    }

    private record StoredCredential(long id, byte[] digest, boolean revoked) {
    }

    private record StoredVerifier(long id, byte[] digest) {
    }
}
