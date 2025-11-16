package io.github.jockerCN.page;

import io.github.jockerCN.jpa.pojo.BaseQueryParam;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface ArgumentResolverAround {

    void resolveBefore(String model,BaseQueryParam queryParam);

    void resolveAfter(String model,BaseQueryParam queryParam);

    boolean support(BaseQueryParam queryParam);
}
