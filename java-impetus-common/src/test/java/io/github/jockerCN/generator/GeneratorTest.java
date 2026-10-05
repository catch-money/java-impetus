package io.github.jockerCN.generator;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTest {

    private static final long EPOCH = 1735689600000L;

    @Test
    void encodesAndDecodesWorkerAndSequence() {
        long now = EPOCH + 1_000;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2, 3, 10, () -> now);
        long first = generator.nextId();
        long second = generator.nextId();
        assertTrue(second > first);
        assertEquals(67, generator.workerId());
        SnowflakeIdGenerator.IdParts parts = SnowflakeIdGenerator.parse(second);
        assertEquals(Instant.ofEpochMilli(now), parts.timestamp());
        assertEquals(2, parts.dataCenterId());
        assertEquals(3, parts.machineId());
        assertEquals(1, parts.sequence());
        assertEquals(1023, SnowflakeIdGenerator.forWorkerId(1023).workerId());
        assertThrows(IllegalArgumentException.class, () -> SnowflakeIdGenerator.forWorkerId(1024));
    }

    @Test
    void rejectsClockRollbackAndOutOfRangeTime() {
        AtomicLong clock = new AtomicLong(EPOCH + 100);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, 3, clock::get);
        generator.nextId();
        clock.decrementAndGet();
        assertThrows(IllegalStateException.class, generator::nextId);
        assertThrows(IllegalStateException.class,
                () -> new SnowflakeIdGenerator(0, 0, 3, () -> EPOCH - 1).nextId());
        long afterRange = EPOCH + (1L << 39);
        assertThrows(IllegalStateException.class,
                () -> new SnowflakeIdGenerator(0, 0, 3, () -> afterRange).nextId());
    }

    @Test
    void waitsForNextMillisecondAfterSequenceWrap() {
        AtomicInteger reads = new AtomicInteger();
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, 3,
                () -> reads.incrementAndGet() <= 16_385 ? EPOCH + 1 : EPOCH + 2);
        for (int i = 0; i < 16_384; i++) {
            generator.nextId();
        }
        SnowflakeIdGenerator.IdParts parts = SnowflakeIdGenerator.parse(generator.nextId());
        assertEquals(Instant.ofEpochMilli(EPOCH + 2), parts.timestamp());
        assertEquals(0, parts.sequence());
    }

    @Test
    void buildsReusableBusinessNumberFromIndependentSegments() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T10:15:30Z"), ZoneOffset.UTC);
        AtomicLong next = new AtomicLong(8);
        SerialNoUtils.Rule rule = SerialNoUtils.rule("-", SerialNoUtils.fixed("ORD"),
                SerialNoUtils.flag("WEB"),
                SerialNoUtils.timestamp(DateTimeFormatter.ofPattern("yyyyMMdd"), clock),
                SerialNoUtils.sequence(next::getAndIncrement, 4));
        assertEquals("ORD-WEB-20260930-0008", rule.next());
        assertEquals("ORD-WEB-20260930-0009", rule.next());
        assertEquals(8, SerialNoUtils.randomSerialNo4().length());
        assertTrue(SerialNoUtils.randomNumberSerialNo(12).matches("[0-9]{12}"));
        assertTrue(SerialNoUtils.randomAlphaNumeric(8).value().matches("[0-9A-Z]{8}"));
        assertThrows(IllegalArgumentException.class, () -> SerialNoUtils.randomDigits(-1));
        assertThrows(IllegalArgumentException.class,
                () -> SerialNoUtils.sequence(() -> -1, 4).value());

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1, 10, () -> EPOCH + 1);
        String id = SerialNoUtils.rule(SerialNoUtils.fixed("X"),
                SerialNoUtils.snowflake(generator)).next();
        assertEquals(1, SnowflakeIdGenerator.parse(Long.parseLong(id.substring(1))).dataCenterId());
        assertEquals(1, SnowflakeIdGenerator.parse(Long.parseLong(id.substring(1))).machineId());
    }

    @Test
    void generatedIdsStayUniqueAcrossConcurrentCallers() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 1);
        Set<Long> ids = java.util.Collections.synchronizedSet(new HashSet<>());
        Thread[] threads = new Thread[4];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 1_000; i++) {
                    ids.add(generator.nextId());
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(4_000, ids.size());
    }
}
