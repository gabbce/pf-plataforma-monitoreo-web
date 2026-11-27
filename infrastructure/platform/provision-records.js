const fs = require("fs");

const server = "mongodb://mongodb:27017/?directConnection=true&serverSelectionTimeoutMS=5000";
const database = "records";
const collection = "operational_records";
const indexName = "instance_record_unique";
const roleName = "records_ingest";
const userName = "records_app";
const writeConcern = { w: 1, j: true };
const actions = ["find", "insert", "update"];
const privilege = { resource: { db: database, collection }, actions };
const assignedRole = { role: roleName, db: database };

function secret(filename) {
    return fs.readFileSync("/run/secrets/" + filename, "utf8").replace(/[\r\n]+$/, "");
}

function matchesRole(role) {
    if (role.roles.length !== 0 || role.privileges.length !== 1) {
        return false;
    }
    const actual = role.privileges[0];
    return actual.resource.db === database && actual.resource.collection === collection &&
        Object.keys(actual.resource).length === 2 &&
        actual.actions.length === actions.length && actions.every(action => actual.actions.includes(action));
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
    if (existingRole) {
        assert(matchesRole(existingRole));
    }

    step = "verificar la cuenta de Registros";
    const existingUser = records.getUser(userName);
    if (existingUser) {
        assert(existingUser.roles.length === 1 && existingUser.roles[0].role === roleName &&
            existingUser.roles[0].db === database);
        const existingConnection = new Mongo(server);
        assert(existingConnection.getDB(database).auth(userName, recordsPassword).ok === 1);
    }

    step = "crear o verificar el índice único de originales";
    const info = records.getCollectionInfos({ name: collection })[0];
    if (info) {
        assert(info.type === "collection" && (!info.options.collation || info.options.collation.locale === "simple"));
    }
    const indexKey = { instanceId: 1, recordId: 1 };
    const indexResult = records.runCommand({
        createIndexes: collection,
        indexes: [{ key: indexKey, name: indexName, unique: true }],
        writeConcern
    });
    assert(indexResult.ok === 1 && !indexResult.writeConcernError);
    const originals = records.getCollection(collection);
    const index = originals.getIndexes().find(candidate => candidate.name === indexName);
    assert(index && JSON.stringify(index.key) === JSON.stringify(indexKey) && index.unique &&
        !index.sparse && !index.partialFilterExpression && !index.hidden &&
        (!index.collation || index.collation.locale === "simple"));

    if (!existingRole) {
        step = "crear el rol de Registros";
        const result = records.runCommand({
            createRole: roleName, roles: [], privileges: [privilege], writeConcern
        });
        assert(result.ok === 1 && !result.writeConcernError);
    }
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
    print("Aprovisionamiento de MongoDB para Registros verificado.");
} catch (failure) {
    // Keep credentials and command details out of provisioning logs.
    print("No se pudo " + step + " en MongoDB.");
    quit(1);
}
