package io.github.jockerCN.generator;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.zip.CRC32;

/**
 * Snowflake-style positive IDs: 39-bit milliseconds since 2025-01-01 UTC,
 * 5-bit data center, 5-bit machine, and 14-bit sequence.
 * A data-center/machine pair must be unique across simultaneously running nodes.
 */
@Slf4j
public class SnowflakeIdGenerator {

    private static final long START_TIMESTAMP = 1735689600000L;
    private static final int SEQUENCE_BITS = 14;
    private static final int MACHINE_ID_BITS = 5;
    private static final int DATA_CENTER_ID_BITS = 5;
    private static final int MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final int DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATA_CENTER_ID_BITS;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
    private static final long MAX_DATA_CENTER_ID = (1L << DATA_CENTER_ID_BITS) - 1;
    private static final long MAX_TIMESTAMP_DELTA = (1L << (Long.SIZE - TIMESTAMP_SHIFT - 1)) - 1;

    private final long dataCenterId;
    private final long machineId;
    private final int maxAttempts;
    private final LongSupplier clock;
    private long sequence;
    private long lastTimestamp = -1L;

    /**
     * Convenience constructor for a single-node setup. IP hashing is not a
     * distributed worker-ID allocator; use explicit IDs for multiple nodes.
     */
    public SnowflakeIdGenerator(int maxCenterData) {
        this(getDefaultDataCenterId(maxCenterData), 1);
    }

    public SnowflakeIdGenerator(long dataCenterId, long machineId) {
        this(dataCenterId, machineId, 0);
    }

    public SnowflakeIdGenerator(long dataCenterId, long machineId, int maxAttempts) {
        this(dataCenterId, machineId, maxAttempts, System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long dataCenterId, long machineId, int maxAttempts, LongSupplier clock) {
        if (dataCenterId < 0 || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("Data center ID must be in [0, 31]");
        }
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("Machine ID must be in [0, 31]");
        }
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
        this.maxAttempts = maxAttempts <= 0 ? 1_000_000 : maxAttempts;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Converts a unique 10-bit worker ID into data-center and machine IDs. */
    public static SnowflakeIdGenerator forWorkerId(int workerId) {
        if (workerId < 0 || workerId > 1023) {
            throw new IllegalArgumentException("Worker ID must be in [0, 1023]");
        }
        return new SnowflakeIdGenerator(workerId >>> MACHINE_ID_BITS, workerId & MAX_MACHINE_ID);
    }

    public int workerId() {
        return (int) ((dataCenterId << MACHINE_ID_BITS) | machineId);
    }

    public String nextIdAsString(String prefix) {
        return Objects.requireNonNull(prefix, "prefix") + nextIdAsString();
    }

    public String nextIdAsString() {
        return Long.toString(nextId());
    }

    public synchronized long nextId() {
        long timestamp = clock.getAsLong();
        if (timestamp < START_TIMESTAMP) {
            throw new IllegalStateException("Clock is before the Snowflake epoch");
        }
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards by "
                    + (lastTimestamp - timestamp) + " ms");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        long elapsed = timestamp - START_TIMESTAMP;
        if (elapsed > MAX_TIMESTAMP_DELTA) {
            throw new IllegalStateException("Snowflake timestamp bits are exhausted");
        }
        lastTimestamp = timestamp;
        return (elapsed << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    private long waitForNextMillis(long previousTimestamp) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long timestamp = clock.getAsLong();
            if (timestamp > previousTimestamp) {
                return timestamp;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Clock did not advance after " + maxAttempts + " attempts");
    }

    /** Decodes an ID produced with this module's epoch and bit layout. */
    public static IdParts parse(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("Snowflake ID must be non-negative");
        }
        long elapsed = id >>> TIMESTAMP_SHIFT;
        return new IdParts(Instant.ofEpochMilli(START_TIMESTAMP + elapsed),
                (id >>> DATA_CENTER_ID_SHIFT) & MAX_DATA_CENTER_ID,
                (id >>> MACHINE_ID_SHIFT) & MAX_MACHINE_ID,
                id & MAX_SEQUENCE);
    }

    public record IdParts(Instant timestamp, long dataCenterId, long machineId, long sequence) {
    }

    private static long getDefaultDataCenterId(int max) {
        if (max < 0 || max > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("Maximum data center ID must be in [0, 31]");
        }
        try {
            String hostAddress = Inet4Address.getLocalHost().getHostAddress();
            CRC32 crc = new CRC32();
            crc.update(hostAddress.getBytes(StandardCharsets.UTF_8));
            return crc.getValue() % (max + 1L);
        } catch (Exception e) {
            log.warn("Failed to resolve local IP; choosing a non-unique fallback data center ID", e);
            return ThreadLocalRandom.current().nextInt(max + 1);
        }
    }

    private static final SnowflakeIdGenerator DEFAULT_INSTANCE = new SnowflakeIdGenerator(1);

    /** Single-process convenience instance; not a cross-host uniqueness guarantee. */
    public static SnowflakeIdGenerator getInstance() {
        return DEFAULT_INSTANCE;
    }
}
