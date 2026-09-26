package testfixture.jpa.queryvalue;

import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.QueryDefault;
import io.github.jockerCN.jpa.annotation.where.Equals;

@JpaQuery(ValueWiringQueryParam.TestEntity.class)
public class ValueWiringQueryParam {

    @Equals
    @QueryDefault(WiringOwnerProvider.class)
    public Long ownerId;

    public static class TestEntity {
        public Long ownerId;
    }
}
