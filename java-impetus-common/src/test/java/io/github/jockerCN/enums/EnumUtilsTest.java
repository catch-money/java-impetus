package io.github.jockerCN.enums;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EnumUtilsTest {

    @Test
    void looksUpPropertiesWithDifferentTypes() {
        assertEquals(State.ACTIVE, EnumUtils.findBy(State.class, State::getValue, 1));
        assertEquals(State.ACTIVE, EnumUtils.findBy(State.class, State::getDesc, "active"));
        assertEquals(State.ACTIVE, EnumUtils.requireBy(State.class, State::getValue, 1));
        assertEquals(State.ACTIVE, EnumUtils.requireBy(State.class, State::getDesc, "active"));
        assertEquals(State.UNKNOWN, EnumUtils.findBy(State.class, State::getValue, null));
        assertEquals(State.UNKNOWN, EnumUtils.findBy(State.class, State::getDesc, null));
        assertNull(EnumUtils.findBy(State.class, State::getValue, 9));
        assertNull(EnumUtils.findBy(State.class, State::getDesc, "missing"));
    }

    @Test
    void supportsNameOrdinalFallbackAndCustomLookup() {
        assertEquals(State.ACTIVE, EnumUtils.getEnumByName("ACTIVE", State.class));
        assertEquals(State.ACTIVE, EnumUtils.getEnumByOrdinal(0, State.class));
        assertEquals(State.DISABLED, EnumUtils.find(State.class, state -> state.getValue() == 0));
        assertNull(EnumUtils.getEnumByName("active", State.class));
        assertNull(EnumUtils.getEnumByOrdinal(-1, State.class));
        assertEquals(State.UNKNOWN,
                EnumUtils.findByOrDefault(State.class, State::getValue, 9, State.UNKNOWN));
        assertEquals(State.UNKNOWN,
                EnumUtils.findByOrDefault(State.class, State::getDesc, "missing", State.UNKNOWN));
        assertThrows(IllegalArgumentException.class,
                () -> EnumUtils.requireBy(State.class, State::getValue, 9));
        assertThrows(IllegalArgumentException.class,
                () -> EnumUtils.requireBy(State.class, State::getDesc, "missing"));
    }

    @Test
    void matchesAnyProperty() {
        assertEquals(Signal.READY, EnumUtils.findBy(Signal.class, Signal::code, "R"));
        assertEquals(Signal.READY, EnumUtils.findBy(Signal.class, Signal::label, "ready"));
        assertEquals(Signal.OFF, EnumUtils.findBy(Signal.class, Signal::enabled, false));
        assertNull(EnumUtils.findBy(Signal.class, Signal::code, "missing"));
        assertEquals(Signal.OFF,
                EnumUtils.findByOrDefault(Signal.class, Signal::code, "missing", Signal.OFF));
        assertEquals(Signal.READY, EnumUtils.requireBy(Signal.class, Signal::code, "R"));
        assertThrows(IllegalArgumentException.class,
                () -> EnumUtils.requireBy(Signal.class, Signal::code, "missing"));
    }

    @Test
    void concurrentFirstLookupsDoNotShareMutableCache() {
        CompletableFuture<?>[] futures = IntStream.range(0, 100)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    assertEquals(State.ACTIVE, EnumUtils.findBy(State.class, State::getValue, 1));
                    assertEquals(State.DISABLED, EnumUtils.findBy(State.class, State::getDesc, "disabled"));
                }))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }

    private enum State {
        ACTIVE(1, "active"), DISABLED(0, "disabled"), UNKNOWN(null, null);

        private final Integer value;
        private final String desc;

        State(Integer value, String desc) {
            this.value = value;
            this.desc = desc;
        }

        public Integer getValue() {
            return value;
        }

        public String getDesc() {
            return desc;
        }
    }

    private enum Signal {
        READY("R", "ready", true), OFF("O", "off", false);

        private final String code;
        private final String label;
        private final boolean enabled;

        Signal(String code, String label, boolean enabled) {
            this.code = code;
            this.label = label;
            this.enabled = enabled;
        }

        String code() {
            return code;
        }

        String label() {
            return label;
        }

        boolean enabled() {
            return enabled;
        }
    }

}
