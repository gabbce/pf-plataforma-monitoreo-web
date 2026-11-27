const fs = require("fs");

const server = "mongodb://mongodb:27017/?directConnection=true&serverSelectionTimeoutMS=5000";
const database = "records";
const collection = "operational_records";
const visibilityCollection = "record_visibility";
const roleName = "records_ingest";
const userName = "records_app";
const writeConcern = { w: 1, j: true };
const actions = ["find", "insert", "update"];
const privileges = [collection, visibilityCollection].map(name => ({
    resource: { db: database, collection: name }, actions
}));
const assignedRole = { role: roleName, db: database };

function secret(filename) {
    return fs.readFileSync("/run/secrets/" + filename, "utf8").replace(/[\r\n]+$/, "");
}

function matchesRole(role, expected) {
    if (role.roles.length !== 0 || role.privileges.length !== expected.length) {
        return false;
    }
    return expected.every(privilege => role.privileges.some(actual =>
        actual.resource.db === database && actual.resource.collection === privilege.resource.collection &&
        Object.keys(actual.resource).length === 2 &&
        actual.actions.length === actions.length && actions.every(action => actual.actions.includes(action))));
}

function assertSimpleCollection(info) {
    assert(info.type === "collection" && (!info.options.collation || info.options.collation.locale === "simple"));
}

let step = "leer secretos";
try {
    const rootPassword = secret("mongo_password");
    const recordsPassword = secret("records_mongo_password");
    assert(rootPassword && recordsPassword && rootPassword !== recordsPassword);

    step = "autenticar la cuenta administrativa";
    const connection = new Mongo(server);
    assert(connection.getDB("admin").auth("root", rootPassword).ok === 1);
    const records = connection.getDB(database);

    step = "verificar el rol de Registros";
    const existingRole = records.getRole(roleName, { showPrivileges: true });
    const legacyRole = existingRole && matchesRole(existingRole, privileges.slice(0, 1));
    if (existingRole) {
        assert(legacyRole || matchesRole(existingRole, privileges));
    }

    step = "verificar la cuenta de Registros";
    const existingUser = records.getUser(userName);
    if (existingUser) {
        assert(existingUser.roles.length === 1 && existingUser.roles[0].role === roleName &&
            existingUser.roles[0].db === database);
        const existingConnection = new Mongo(server);
        assert(existingConnection.getDB(database).auth(userName, recordsPassword).ok === 1);
    }

    step = "crear o verificar los índices de originales";
    const info = records.getCollectionInfos({ name: collection })[0];
    if (info) {
        assertSimpleCollection(info);
    }
    const indexes = [
        { key: { instanceId: 1, recordId: 1 }, name: "instance_record_unique", unique: true },
        {
            key: { instanceId: 1, storageClass: 1, "original.definitionId": 1,
                occurredAtSecond: -1, occurredAtNano: -1, recordId: -1 },
            name: "latest_operational_occurrence"
        }
    ];
    const indexResult = records.runCommand({
        createIndexes: collection, indexes, writeConcern
    });
    assert(indexResult.ok === 1 && !indexResult.writeConcernError);
    const installedIndexes = records.getCollection(collection).getIndexes();
    for (const expected of indexes) {
        const actual = installedIndexes.find(candidate => candidate.name === expected.name);
        assert(actual && JSON.stringify(actual.key) === JSON.stringify(expected.key) &&
            Boolean(actual.unique) === Boolean(expected.unique) &&
            !actual.sparse && !actual.partialFilterExpression && !actual.hidden &&
            (!actual.collation || actual.collation.locale === "simple"));
    }

    step = "crear o verificar la colección de visibilidad";
    const visibility = records.getCollectionInfos({ name: visibilityCollection })[0];
    if (visibility) {
        assertSimpleCollection(visibility);
    } else {
        const result = records.runCommand({ create: visibilityCollection, writeConcern });
        assert(result.ok === 1 && !result.writeConcernError);
    }

    if (!existingRole) {
        step = "crear el rol de Registros";
        const result = records.runCommand({
            createRole: roleName, roles: [], privileges, writeConcern
        });
        assert(result.ok === 1 && !result.writeConcernError);
    } else if (legacyRole) {
        step = "actualizar el rol de Registros para la visibilidad";
        const result = records.runCommand({
            grantPrivilegesToRole: roleName, privileges: privileges.slice(1), writeConcern
        });
        assert(result.ok === 1 && !result.writeConcernError);
    }
    assert(matchesRole(records.getRole(roleName, { showPrivileges: true }), privileges));
    if (!existingUser) {
        step = "crear la cuenta de Registros";
        const result = records.runCommand({
            createUser: userName, pwd: recordsPassword, roles: [assignedRole], writeConcern
        });
        assert(result.ok === 1 && !result.writeConcernError);
    }

    step = "comprobar el acceso de Registros";
    const app = new Mongo(server).getDB(database);
    assert(app.auth(userName, recordsPassword).ok === 1);
    app.getCollection(collection).findOne();
    app.getCollection(visibilityCollection).findOne();
    print("Aprovisionamiento de MongoDB para Registros verificado.");
} catch (failure) {
    // Keep credentials and command details out of provisioning logs.
    print("No se pudo " + step + " en MongoDB.");
    quit(1);
}
