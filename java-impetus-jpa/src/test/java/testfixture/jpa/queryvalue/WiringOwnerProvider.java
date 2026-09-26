package testfixture.jpa.queryvalue;

import io.github.jockerCN.jpa.query.value.QueryValueProvider;

public class WiringOwnerProvider implements QueryValueProvider<Long> {
    @Override
    public Long provide(Object queryParam) {
        return 42L;
    }
}
