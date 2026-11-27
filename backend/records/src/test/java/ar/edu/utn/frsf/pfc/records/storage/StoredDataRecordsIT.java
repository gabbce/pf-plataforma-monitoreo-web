package ar.edu.utn.frsf.pfc.records.storage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ar.edu.utn.frsf.pfc.records.station.DataContextClient.Context;
import ar.edu.utn.frsf.pfc.records.station.DataContextClient.TimeValidity;
import ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.Result;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.Status.ALREADY_STORED;
import static ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.Status.CONFLICT;
import static ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.Status.NOT_FOUND;
import static ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.Status.RETRY;
import static ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.Status.STORED;
import static ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.StorageClass.OPERATIONAL;
import static ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.StorageClass.REVIEW;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.compoundIndex;
import static com.mongodb.client.model.Indexes.descending;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class StoredDataRecordsIT {

    private static final String MONGO_IMAGE = "mongo:9.0.8-resolute@sha256:"
            + "1c2e2674ed3adde7edcfe83f7a7a5cbe0204521161d4199ab34e9ec3da2db5c4";
    private static final GenericContainer<?> MONGO = new GenericContainer<>(DockerImageName.parse(MONGO_IMAGE))
            .withExposedPorts(27017).withCommand("mongod", "--bind_ip_all", "--setParameter", "enableTestCommands=1");

    private static final Instant FIRST_RECEIPT = Instant.parse("2026-11-30T12:00:00.000000007Z");
    private static final Instant LATER_RECEIPT = FIRST_RECEIPT.plusSeconds(15);
    private static final Instant OCCURRENCE = Instant.parse("2026-01-15T12:00:00.123456789Z");
    private static final ConcurrentLinkedQueue<Write> WRITES = new ConcurrentLinkedQueue<>();
    private static final AtomicReference<PausedInsert> PAUSED_INSERT = new AtomicReference<>();

    private static MongoClient client;
    private static MongoCollection<Document> collection;
    private StoredDataRecords storage;

    @BeforeAll
    static void start() {
        MONGO.start();
        var uri = "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017)
                + "/?timeoutMS=5000&retryWrites=false&retryReads=false";
        client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .addCommandListener(new CommandListener() {
                    @Override
                    public void commandStarted(CommandStartedEvent event) {
                        if (List.of("insert", "update").contains(event.getCommandName())) {
                            WRITES.add(new Write(event.getCommandName(), event.getCommand().getDocument("writeConcern")));
                        }
                        var pause = PAUSED_INSERT.get();
                        if (pause != null && event.getCommandName().equals("insert")
                                && event.getCommand().getString("insert").getValue()
                                        .equals(StoredDataRecords.COLLECTION)) {
                            pause.started().countDown();
                            try {
                                if (!pause.release().await(10, TimeUnit.SECONDS)) {
                                    throw new AssertionError("Timed out waiting for the visibility cut");
                                }
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(failure);
                            }
                        }
                    }
                })
                .build());
        collection = client.getDatabase("records").getCollection(StoredDataRecords.COLLECTION);
        collection.createIndex(ascending("instanceId", "recordId"),
                new IndexOptions().unique(true).name("instance_record_unique"));
        collection.createIndex(compoundIndex(ascending("instanceId", "storageClass", "original.definitionId"),
                descending("occurredAtSecond", "occurredAtNano", "recordId")));
        client.getDatabase("records").createCollection(MeasurementReads.STATE_COLLECTION);
    }

    @AfterAll
    static void stop() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            MONGO.stop();
        }
    }

    @BeforeEach
    void prepareStorage() {
        collection.deleteMany(new Document());
        visibilityState().deleteMany(new Document());
        WRITES.clear();
        storage = storageAt(FIRST_RECEIPT);
    }

    @Test
    void preservesTheOriginalAndItsHistoricalContextAcrossEquivalentReplays() {
        var original = record("000123", "same-id", "0.00");
        assertThat(storage.recognize(original).status()).isEqualTo(NOT_FOUND);
        assertThat(storage.store(original, context("rpm-a"))).isEqualTo(new Result(STORED, OPERATIONAL, FIRST_RECEIPT));

        var first = stored("000123", "same-id");
        var envelope = first.get("original", Document.class);
        assertThat(envelope).containsEntry("instanceId", "000123").containsEntry("producerId", "station-a")
                .containsEntry("recordId", "same-id").containsEntry("schemaVersion", 1).containsEntry("kind", "DATA")
                .containsEntry("sequence", 42L).containsEntry("appliedConfigVersion", "config-a")
                .containsEntry("occurredAt", OCCURRENCE.toString());
        assertThat(envelope.get("payload", Document.class)).containsEntry("value", "0.00").containsEntry("quality", "VALID");
        assertThat(first.get("context", Document.class)).containsEntry("sourceId", "rpm-a");
        assertThat(storageAt(LATER_RECEIPT).store(record("000123", "same-id", "0"), context("rpm-b")))
                .isEqualTo(new Result(ALREADY_STORED, OPERATIONAL, FIRST_RECEIPT));

        var replayed = stored("000123", "same-id");
        assertThat(replayed.remove("durabilityFence")).isInstanceOf(ObjectId.class);
        assertThat(replayed).isEqualTo(first);
        var journaled = BsonDocument.parse("{w: 1, j: true}");
        assertThat(WRITES).containsExactly(new Write("insert", journaled), new Write("insert", journaled),
                new Write("update", journaled));
    }

    @Test
    void confinesConflictAndRecognitionToTheSameInstance() {
        var original = record("000123", "same-id", "1.234567890123456789012345678901234567890123");
        assertThat(storage.store(original, context("rpm-a")).status()).isEqualTo(STORED);
        var first = stored("000123", "same-id");

        assertThat(storage.recognize(record("000123", "same-id", "35")).status()).isEqualTo(CONFLICT);
        assertThat(storage.store(record("000123", "same-id", "36"), context("rpm-a")).status()).isEqualTo(CONFLICT);
        assertThat(storage.recognize(record("000124", "same-id", "35")).status()).isEqualTo(NOT_FOUND);
        assertThat(storage.store(record("000124", "same-id", "35"), context("rpm-b")).status()).isEqualTo(STORED);

        var afterConflict = stored("000123", "same-id");
        assertThat(afterConflict.remove("durabilityFence")).isInstanceOf(ObjectId.class);
        assertThat(afterConflict).isEqualTo(first);
        assertThat(first.getEmbedded(List.of("original", "payload", "value"), String.class))
                .isEqualTo("1.234567890123456789012345678901234567890123");
        assertThat(collection.countDocuments()).isEqualTo(2);
    }

    @Test
    void preservesReviewWithAnInvalidMarkOrNoMark() {
        assertThat(storage.store(review("marked", OCCURRENCE), context(null)))
                .isEqualTo(new Result(STORED, REVIEW, FIRST_RECEIPT));
        assertThat(storage.store(review("unmarked", null), context(null)))
                .isEqualTo(new Result(STORED, REVIEW, FIRST_RECEIPT));
        assertThat(stored("000123", "marked").get("original", Document.class))
                .containsEntry("timeValidity", "INVALID").containsEntry("occurredAt", OCCURRENCE.toString());
        var unmarked = stored("000123", "unmarked");
        assertThat(unmarked.get("original", Document.class)).containsEntry("occurredAt", null);
        assertThat(unmarked.get("context", Document.class)).containsEntry("sourceId", null);
        assertThat(unmarked.getEmbedded(List.of("original", "payload"), Document.class)).containsEntry("value", null);
        assertThat(storage.recognize(review("unmarked", null))).isEqualTo(new Result(ALREADY_STORED, REVIEW, FIRST_RECEIPT));
    }

    @Test
    void refusesToStoreAnOriginalWithAContradictoryCatalogSubsystem() {
        var original = record("000123", "wrong-subsystem", "0");
        var wrongContext = new Context("embolsadora", "engine.rpm", "drive", "NUMBER", "rpm", "rpm-a");

        assertThatThrownBy(() -> storage.store(original, wrongContext)).isInstanceOf(IllegalArgumentException.class);
        assertThat(collection.countDocuments()).isZero();
    }

    @Test
    void resolvesConcurrentInsertsWithOneWinner() throws Exception {
        var original = record("000123", "simultaneous", "8");
        var launch = new CountDownLatch(1);
        var calls = new ArrayList<Future<Result>>();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                calls.add(workers.submit(() -> {
                    launch.await();
                    return storage.store(original, context("rpm-a"));
                }));
            }
            launch.countDown();

            var results = new ArrayList<StoredDataRecords.Status>();
            for (var call : calls) {
                var result = call.get(12, TimeUnit.SECONDS);
                assertThat(result.receivedAt()).isEqualTo(FIRST_RECEIPT);
                results.add(result.status());
            }
            assertThat(results).containsExactlyInAnyOrder(STORED, ALREADY_STORED, ALREADY_STORED,
                    ALREADY_STORED, ALREADY_STORED);
        }
        assertThat(collection.countDocuments()).isOne();
    }

    @Test
    void retriesAmbiguousInsertAndReplayAcknowledgements() {
        var original = record("000123", "uncertain", "10");
        withOneWriteConcernError("insert", () -> assertThat(storage.store(original, context("rpm-a")).status())
                .isEqualTo(RETRY));
        assertThat(stored("000123", "uncertain").getString("receivedAt")).isEqualTo(FIRST_RECEIPT.toString());

        var retrying = storageAt(LATER_RECEIPT);
        withOneWriteConcernError("update", () -> assertThat(retrying.recognize(original).status()).isEqualTo(RETRY));
        assertThat(retrying.recognize(original)).isEqualTo(new Result(ALREADY_STORED, OPERATIONAL, FIRST_RECEIPT));
        assertThat(collection.countDocuments()).isOne();
    }

    @Test
    void reportsAConflictOnlyAfterConfirmedDurabilityOfTheWinner() {
        var winner = record("000123", "competing", "10");
        var rival = record("000123", "competing", "11");
        withOneWriteConcernError("insert",
                () -> assertThat(storage.store(winner, context("rpm-a")).status()).isEqualTo(RETRY));
        var first = stored("000123", "competing");

        var retrying = storageAt(LATER_RECEIPT);
        withOneWriteConcernError("update", () -> assertThat(retrying.recognize(rival).status()).isEqualTo(RETRY));
        assertThat(retrying.recognize(rival).status()).isEqualTo(CONFLICT);

        var afterConflict = stored("000123", "competing");
        assertThat(afterConflict.remove("durabilityFence")).isInstanceOf(ObjectId.class);
        assertThat(afterConflict).isEqualTo(first);
    }

    @Test
    void boundsTheWaitForAReplayAcknowledgement() {
        var original = record("000123", "delayed-confirmation", "3");
        assertThat(storage.store(original, context("rpm-a")).status()).isEqualTo(STORED);
        var blocking = new Document("blockConnection", true).append("blockTimeMS", 6000);
        withOneFailure("update", blocking, () -> assertTimeout(Duration.ofSeconds(4),
                () -> assertThat(storage.recognize(original).status()).isEqualTo(RETRY)));
    }

    @Test
    void keepsAnEarlierReceiptOutOfACutWhileTheInsertIsInFlight() throws Exception {
        var clock = new MutableClock(LATER_RECEIPT);
        var readings = readings(clock);
        readings.prepare();
        var reading = record("000123", "in-flight", "19");
        var pause = new PausedInsert(new CountDownLatch(1), new CountDownLatch(1));
        PAUSED_INSERT.set(pause);

        Instant cut;
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var insertion = workers.submit(() -> {
                assertThat(storage.store(reading, context("rpm-a"))).isEqualTo(
                        new Result(STORED, OPERATIONAL, FIRST_RECEIPT));
                readings.confirmVisible(reading.instanceId(), reading.recordId());
            });
            try {
                assertThat(pause.started().await(5, TimeUnit.SECONDS)).isTrue();
                var before = readings.latest("000123");
                cut = before.asOf();
                assertThat(FIRST_RECEIPT).isBefore(cut);
                assertThat(before.items()).isEmpty();
            } finally {
                PAUSED_INSERT.set(null);
                pause.release().countDown();
            }
            insertion.get(10, TimeUnit.SECONDS);
        }

        clock.set(LATER_RECEIPT.plusSeconds(1));
        var restarted = readings(clock);
        assertThat(restarted.latestAt("000123", cut).items()).isEmpty();
        assertThat(restarted.latest("000123").items()).singleElement()
                .satisfies(item -> assertThat(item.measurement().recordId()).isEqualTo("in-flight"));
    }

    @Test
    void doesNotIncludeAnOriginalVisibleInMongoBeforeItsDurabilityIsCertain() {
        var clock = new MutableClock(LATER_RECEIPT);
        var readings = readings(clock);
        readings.prepare();
        var reading = record("000123", "uncertain", "19");

        withOneWriteConcernError("insert",
                () -> assertThat(storage.store(reading, context("rpm-a")).status()).isEqualTo(RETRY));
        assertThat(stored("000123", "uncertain")).isNotNull();
        var old = readings.latest("000123");
        assertThat(old.items()).isEmpty();
        assertThat(storage.recognize(reading).status()).isEqualTo(ALREADY_STORED);
        readings.confirmVisible("000123", reading.recordId());

        clock.set(LATER_RECEIPT.plusSeconds(1));
        assertThat(readings.latestAt("000123", old.asOf()).items()).isEmpty();
        assertThat(readings.latest("000123").items()).singleElement()
                .satisfies(item -> assertThat(item.measurement().receivedAt()).isEqualTo(FIRST_RECEIPT));
    }

    @Test
    void recoversAnInterruptedPublicationWithoutMovingAnEarlierCutOrAcceptingAStaleWriter() {
        var clock = new MutableClock(LATER_RECEIPT);
        var readings = readings(clock);
        var old = readings.latest("000123");
        var reading = record("000123", "pending", "19");
        assertThat(storage.store(reading, context("rpm-a")).status()).isEqualTo(STORED);

        // A durable reservation can survive the process even before the marker is written.
        var state = visibilityState();
        var oldRevision = state.find().first().getObjectId("revision");
        var original = stored("000123", "pending");
        var position = LATER_RECEIPT.getEpochSecond() * 1_000_000 + 1;
        state.updateOne(eq("_id", "latest"), combine(set("floorMicros", position),
                set("pending", new Document("originalId", original.getObjectId("_id")).append("position", position))));
        withOneWriteConcernError("update",
                () -> assertThatThrownBy(() -> readings.latest("000123")).isInstanceOf(MongoException.class));

        clock.set(LATER_RECEIPT.plusSeconds(1));
        var restarted = readings(clock);
        assertThat(restarted.latestAt("000123", old.asOf())).isEqualTo(old);
        assertThat(restarted.latest("000123").items()).singleElement()
                .satisfies(item -> assertThat(item.measurement().recordId()).isEqualTo("pending"));
        assertThat(stored("000123", "pending").getLong("visibleAtMicros")).isEqualTo(position);
        assertThat(state.find().first().get("pending")).isNull();
        assertThat(state.updateOne(and(eq("_id", "latest"), eq("revision", oldRevision)),
                set("floorMicros", 0L)).getMatchedCount()).isZero();
    }

    @Test
    void returnsOnlyOperationalLatestValuesWithFullOccurrencePrecisionAndStoredContext() {
        var clock = new MutableClock(LATER_RECEIPT);
        var readings = readings(clock);
        var boundary = Instant.parse("2026-01-15T12:00:01Z");
        var fractional = boundary.plusNanos(1);
        var rpmSource = context("rpm-historical");

        assertThat(storage.store(measurement("whole-second", "engine.rpm", boundary, BigDecimal.TEN,
                DataRecord.Quality.VALID, "rpm"), rpmSource).status()).isEqualTo(STORED);
        assertThat(storage.store(measurement("tie-a", "engine.rpm", fractional, BigDecimal.ONE,
                DataRecord.Quality.VALID, "rpm"), rpmSource).status()).isEqualTo(STORED);
        assertThat(storage.store(measurement("tie-z", "engine.rpm", fractional, BigDecimal.ZERO,
                DataRecord.Quality.VALID, "rpm"), rpmSource).status()).isEqualTo(STORED);
        assertThat(storageAt(LATER_RECEIPT).store(measurement("late", "engine.rpm", boundary.minusNanos(1),
                BigDecimal.TEN, DataRecord.Quality.VALID, "rpm"), rpmSource).status()).isEqualTo(STORED);
        var coolantContext = new Context("embolsadora", "engine.coolant_temperature", "engine", "NUMBER", "°C",
                "coolant-historical");
        assertThat(storage.store(measurement("invalid", "engine.coolant_temperature", fractional, BigDecimal.TEN,
                DataRecord.Quality.INVALID, "°C"), coolantContext).status()).isEqualTo(STORED);
        assertThat(storage.store(review("review", boundary.plusSeconds(5)), context(null)).status()).isEqualTo(STORED);
        var original = stored("000123", "tie-z");

        // This collection predates the visibility metadata. Preparation leaves the originals intact.
        readings.prepare();
        clock.set(LATER_RECEIPT.plusSeconds(1));
        assertThat(readings.latest("000123").items()).satisfiesExactly(rpm -> {
            assertThat(rpm.freshness()).isEqualTo(MeasurementReads.Freshness.UNKNOWN);
            assertThat(rpm.measurement().recordId()).isEqualTo("tie-z");
            assertThat(rpm.measurement().value()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(rpm.measurement().occurredAt()).isEqualTo(fractional);
            assertThat(rpm.measurement().context()).isEqualTo(
                    new MeasurementReads.HistoricalContext("rpm-historical", "config-a"));
        }, coolant -> {
            assertThat(coolant.measurement().value()).isNull();
            assertThat(coolant.measurement().quality()).isEqualTo(DataRecord.Quality.INVALID);
        });
        var migrated = stored("000123", "tie-z");
        assertThat(migrated.entrySet()).containsAll(original.entrySet());
        assertThat(stored("000123", "review")).doesNotContainKey("visibleAtMicros");
        assertThat(readings.latest("000124").items()).isEmpty();
        assertThat(WRITES).allSatisfy(write -> assertThat(write.concern()).isEqualTo(
                BsonDocument.parse("{w: 1, j: true}")));
    }

    private static void withOneWriteConcernError(String command, Runnable operation) {
        var error = new Document("writeConcernError",
                new Document("code", 64).append("errmsg", "Simulated uncertain acknowledgement"));
        withOneFailure(command, error, operation);
    }

    private static void withOneFailure(String command, Document failure, Runnable operation) {
        var admin = client.getDatabase("admin");
        admin.runCommand(new Document("configureFailPoint", "failCommand").append("mode", new Document("times", 1))
                .append("data", failure.append("failCommands", List.of(command))));
        try {
            operation.run();
        } finally {
            admin.runCommand(new Document("configureFailPoint", "failCommand").append("mode", "off"));
        }
    }

    private static StoredDataRecords storageAt(Instant instant) {
        return new StoredDataRecords(collection, Clock.fixed(instant, ZoneOffset.UTC), 2000);
    }

    private static MeasurementReads readings(Clock clock) {
        return new MeasurementReads(client.getDatabase("records"), clock, 2000);
    }

    private static MongoCollection<Document> visibilityState() {
        return client.getDatabase("records").getCollection(MeasurementReads.STATE_COLLECTION)
                .withWriteConcern(WriteConcern.W1.withJournal(true));
    }

    private static Document stored(String instance, String recordId) {
        return collection.find(and(eq("instanceId", instance), eq("recordId", recordId))).first();
    }

    private static Context context(String sourceId) {
        return new Context("embolsadora", "engine.rpm", "engine", "NUMBER", "rpm", sourceId);
    }

    private static DataRecord record(String instance, String id, String number) {
        return new DataRecord(1, instance, id, "station-a", 42L, "engine.rpm", "engine", OCCURRENCE,
                TimeValidity.VALID, "config-a",
                new DataRecord.Payload(new BigDecimal(number), "rpm", DataRecord.Quality.VALID));
    }

    private static DataRecord review(String id, Instant mark) {
        return new DataRecord(1, "000123", id, "station-a", 43L, "engine.rpm", "engine", mark,
                TimeValidity.INVALID, null, new DataRecord.Payload(null, "rpm", DataRecord.Quality.MISSING));
    }

    private static DataRecord measurement(String id, String definition, Instant occurrence, BigDecimal number,
            DataRecord.Quality quality, String unit) {
        return new DataRecord(1, "000123", id, "station-a", 1L, definition, "engine", occurrence,
                TimeValidity.VALID, "config-a", new DataRecord.Payload(number, unit, quality));
    }

    private record PausedInsert(CountDownLatch started, CountDownLatch release) {
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        private void set(Instant value) {
            instant.set(value);
        }
    }

    private record Write(String command, BsonDocument concern) {
    }
}
