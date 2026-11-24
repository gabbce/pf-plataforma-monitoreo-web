package ar.edu.utn.frsf.pfc.management.web;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoWebAccountLoader {

    private static final String CHASSIS = "000123";
    private static final OffsetDateTime ASSOCIATION_START = OffsetDateTime.parse("2026-01-15T12:00:00Z");

    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;

    DemoWebAccountLoader(JdbcClient jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Transactional
    public void load(String technicianPassword, String ownerPassword, String unassociatedOwnerPassword) {
        var accounts = List.of(
                new DemoAccount("tecnico-ejemplo", "Servicio Técnico de ejemplo",
                        WebCredentials.TECHNICAL_SERVICE, null, technicianPassword),
                new DemoAccount("propietario-ejemplo", "Propietario de ejemplo",
                        WebCredentials.OWNER, CHASSIS, ownerPassword),
                new DemoAccount("propietario-sin-asociacion", "Propietario sin asociación",
                        WebCredentials.OWNER, null, unassociatedOwnerPassword));
        accounts.forEach(account -> validatePassword(account.password()));

        // Coordinate these loads with the catalog command and with one another.
        jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(158303, 1)").query(Integer.class).single();
        if (!jdbc.sql("SELECT EXISTS (SELECT 1 FROM machine_instance WHERE instance_id = :chassis)")
                .param("chassis", CHASSIS).query(Boolean.class).single()) {
            throw new IllegalStateException("Run load-demo-fixture before load-demo-web-accounts");
        }
        ensureNamed("web_permission", "permission_id", WebCredentials.MACHINE_READ, "Consultar máquinas");
        ensureRole(WebCredentials.TECHNICAL_SERVICE, "Servicio Técnico");
        ensureRole(WebCredentials.OWNER, "Propietario");
        accounts.forEach(this::ensureAccount);
    }

    private static void validatePassword(String password) {
        if (password.isBlank() || !WebCredentials.supports(password)) {
            throw new IllegalArgumentException("Demo passwords must be nonblank and at most 72 UTF-8 bytes");
        }
    }

    private boolean ensureNamed(String table, String key, String id, String name) {
        var inserted = jdbc.sql("""
                INSERT INTO %s (%s, name) VALUES (:id, :name) ON CONFLICT DO NOTHING
                """.formatted(table, key))
                .param("id", id).param("name", name).update();
        auditIfInserted(inserted, table, id);
        var existing = jdbc.sql("SELECT name FROM %s WHERE %s = :id FOR UPDATE".formatted(table, key))
                .param("id", id).query(String.class).single();
        if (!name.equals(existing)) {
            throw conflict(table, id);
        }
        return inserted == 1;
    }

    private void ensureRole(String role, String name) {
        if (ensureNamed("web_role", "role_id", role, name)) {
            var inserted = jdbc.sql("""
                    INSERT INTO web_role_permission (role_id, permission_id) VALUES (:role, :permission)
                    """)
                    .param("role", role).param("permission", WebCredentials.MACHINE_READ).update();
            auditIfInserted(inserted, "web_role_permission", role + "/" + WebCredentials.MACHINE_READ);
        }
        var permissions = jdbc.sql("SELECT permission_id FROM web_role_permission WHERE role_id = :role")
                .param("role", role).query(String.class).list();
        if (!permissions.equals(List.of(WebCredentials.MACHINE_READ))) {
            throw conflict("permissions for web_role", role);
        }
    }

    private void ensureAccount(DemoAccount account) {
        var email = account.identifier() + "@example.invalid";
        var createdUser = jdbc.sql("""
                INSERT INTO web_user (identifier, name, email, active, password_hash)
                VALUES (:identifier, :name, :email, true, :password)
                ON CONFLICT DO NOTHING RETURNING user_id
                """)
                .param("identifier", account.identifier())
                .param("name", account.name())
                .param("email", email)
                .param("password", passwords.encode(account.password()))
                .query(Long.class).optional();
        var user = jdbc.sql("""
                SELECT user_id, name, email, active, password_hash
                FROM web_user WHERE identifier = :identifier FOR UPDATE
                """)
                .param("identifier", account.identifier())
                .query((row, index) -> new ExistingUser(row.getLong(1), row.getString(2), row.getString(3),
                        row.getBoolean(4), row.getString(5)))
                .optional()
                .orElseThrow(() -> conflict("web_user", account.identifier()));
        if (!user.active() || !user.name().equals(account.name()) || !user.email().equals(email)
                || !passwords.matches(account.password(), user.passwordHash())) {
            throw conflict("web_user", account.identifier());
        }
        if (createdUser.isPresent()) {
            auditIfInserted(1, "web_user", Long.toString(user.id()));
            var insertedRole = jdbc.sql("""
                    INSERT INTO web_user_role (user_id, role_id) VALUES (:user, :role)
                    """)
                    .param("user", user.id()).param("role", account.role()).update();
            auditIfInserted(insertedRole, "web_user_role", user.id() + "/" + account.role());
        }
        var roles = jdbc.sql("SELECT role_id FROM web_user_role WHERE user_id = :user")
                .param("user", user.id()).query(String.class).list();
        if (!roles.equals(List.of(account.role()))) {
            throw conflict("roles for web_user", account.identifier());
        }
        ensureAssociation(user.id(), account, createdUser.isPresent());
    }

    private void ensureAssociation(long userId, DemoAccount account, boolean created) {
        if (created && account.chassis() != null) {
            var inserted = jdbc.sql("""
                    INSERT INTO web_machine_association (user_id, instance_id, link_type, valid_from)
                    VALUES (:user, :instance, 'OWNER', :since)
                    """)
                    .param("user", userId)
                    .param("instance", account.chassis())
                    .param("since", ASSOCIATION_START)
                    .update();
            auditIfInserted(inserted, "web_machine_association", userId + "/" + account.chassis());
        }
        var associations = jdbc.sql("""
                SELECT instance_id, link_type, valid_from, valid_until FROM web_machine_association WHERE user_id = :user
                """)
                .param("user", userId)
                .query((row, index) -> new Association(row.getString(1), row.getString(2),
                        row.getObject(3, OffsetDateTime.class), row.getObject(4, OffsetDateTime.class)))
                .list();
        var expected = account.chassis() == null ? List.of()
                : List.of(new Association(account.chassis(), WebCredentials.OWNER, ASSOCIATION_START, null));
        if (!associations.equals(expected)) {
            throw conflict("associations for web_user", account.identifier());
        }
    }

    private void auditIfInserted(int inserted, String table, String id) {
        if (inserted == 1) {
            jdbc.sql("""
                    INSERT INTO audit_record (actor_type, actor_reference, action, element_type, element_id, occurred_at)
                    VALUES ('SISTEMA', 'p1-demo-web-accounts', 'CREATE', :table, :id, CURRENT_TIMESTAMP)
                    """)
                    .param("table", table).param("id", id).update();
        }
    }

    private static IllegalStateException conflict(String table, String id) {
        return new IllegalStateException("Conflicting demo web account element " + table + " " + id);
    }

    private record DemoAccount(String identifier, String name, String role, String chassis, String password) {
    }

    private record ExistingUser(long id, String name, String email, boolean active, String passwordHash) {
    }

    private record Association(String chassis, String linkType, OffsetDateTime from, OffsetDateTime until) {
    }
}
