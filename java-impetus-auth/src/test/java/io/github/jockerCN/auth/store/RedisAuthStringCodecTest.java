package io.github.jockerCN.auth.store;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import static org.assertj.core.api.Assertions.*;

class RedisAuthStringCodecTest {
    @Test void absentComparisonsAreSafeAndRealValuesKeepTheNativeStringWireFormat() throws Exception {
        ByteBuf absent = RedisAuthStringCodec.INSTANCE.getValueEncoder().encode(null);
        ByteBuf auth = RedisAuthStringCodec.INSTANCE.getValueEncoder().encode("{\"state\":\"有效\"}");
        ByteBuf nativeValue = StringCodec.INSTANCE.getValueEncoder().encode("{\"state\":\"有效\"}");
        try {
            assertThat(absent.readableBytes()).isZero();
            assertThat(auth).isEqualTo(nativeValue).isNotEqualTo(absent);
        } finally { absent.release(); auth.release(); nativeValue.release(); }
    }
}
