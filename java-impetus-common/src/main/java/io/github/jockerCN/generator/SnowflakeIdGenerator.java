package io.github.jockerCN.generator;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;

@SuppressWarnings("unused")
@Slf4j
public class SnowflakeIdGenerator {

    // 开始时间戳 (2025-01-01 00:00:00 UTC)
    private static final long START_TIMESTAMP = 1735689600000L;

    // 各部分位数
    private static final long SEQUENCE_BITS = 14; // 序列号占用位数
    private static final long MACHINE_ID_BITS = 5; // 机器ID占用位数
    private static final long DATA_CENTER_ID_BITS = 5; // 数据中心ID占用位数

    // 每部分最大值
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
    private static final long MAX_DATA_CENTER_ID = (1L << DATA_CENTER_ID_BITS) - 1;

    // 位移偏移量
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATA_CENTER_ID_BITS;

    // 数据中心ID和机器ID
    private final long dataCenterId;
    private final long machineId;
    private final int maxAttempts;
    // 当前毫秒内的序列号
    private long sequence = 0L;

    // 上次生成ID的时间戳
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(int maxCenterData) {
        this(getDefaultDataCenterId(maxCenterData), 1);
    }

    public SnowflakeIdGenerator(long dataCenterId, long machineId) {
        this(dataCenterId, machineId, 0);
    }

    private static long getDefaultDataCenterId(int max) {
        try {
            String hostAddress = Inet4Address.getLocalHost().getHostAddress();
            log.info("Using IP address for DataCenter ID [{}] generation: {}", max, hostAddress);
            return getHost(hostAddress, max);
        } catch (Exception e) {
            log.warn("Failed to get local IP address, using random DataCenter ID", e);
            return ThreadLocalRandom.current().nextInt(32);
        }
    }

    public SnowflakeIdGenerator(long dataCenterId, long machineId, int maxAttempts) {
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException("DataCenter ID out of range");
        }
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException("Machine ID out of range");
        }
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
        this.maxAttempts = maxAttempts <= 0 ? 100_0000 : maxAttempts;
    }


    public String nextIdAsString(String prefix) {
        return String.join("", prefix, nextIdAsString());
    }

    public synchronized String nextIdAsString() {
        return String.valueOf(nextId());
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("System Clock moved backwards. Refusing to generate ID.");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 等待下一毫秒
                currentTimestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT) |
                (dataCenterId << DATA_CENTER_ID_SHIFT) |
                (machineId << MACHINE_ID_SHIFT) |
                sequence;
    }

    private long waitForNextMillis(long lastTimestamp) {
        int attempts = 0;
        long timestamp;

        do {
            timestamp = System.currentTimeMillis();
            if (++attempts >= maxAttempts) {
                // 超过最大尝试次数，返回修正后的时间戳
                log.error("System clock stalled for too long ({} attempts reached). Refusing to generate ID. Last timestamp: [{}].", maxAttempts, lastTimestamp);
                throw new RuntimeException("System clock stalled for too long.");
            }
        } while (timestamp <= lastTimestamp);

        return timestamp;
    }


    private static int getHost(String ipAddress, int max) {
        //CRC32 哈希增强分布均匀性
        CRC32 crc = new CRC32();
        crc.update(ipAddress.getBytes());
        return (int) (crc.getValue() % (max + 1));
    }


    private static final SnowflakeIdGenerator defaultSnowflakeIdGenerator = new SnowflakeIdGenerator(1);

    public static SnowflakeIdGenerator getInstance() {
        return defaultSnowflakeIdGenerator;
    }
}
