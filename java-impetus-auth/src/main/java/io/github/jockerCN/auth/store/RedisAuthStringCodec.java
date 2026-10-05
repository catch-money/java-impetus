package io.github.jockerCN.auth.store;

import io.netty.buffer.Unpooled;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.Encoder;

import java.util.Objects;

/**
 * Internal Redisson 4.7 transactional-CAS compatibility: its Java equality path encodes absent
 * values. Null is an empty comparison sentinel, never a persisted Auth value. All actual strings
 * use the original StringCodec wire format; native Redis CAS still controls absence atomically.
 */
public final class RedisAuthStringCodec extends StringCodec {
    public static final RedisAuthStringCodec INSTANCE = new RedisAuthStringCodec();
    private final Encoder encoder = value -> Objects.isNull(value)
            ? Unpooled.EMPTY_BUFFER : StringCodec.INSTANCE.getValueEncoder().encode(value);

    private RedisAuthStringCodec() {
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }
}
