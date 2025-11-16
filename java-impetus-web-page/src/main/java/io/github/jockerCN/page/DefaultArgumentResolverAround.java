package io.github.jockerCN.page;

import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import org.springframework.stereotype.Component;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Component
public class DefaultArgumentResolverAround implements ArgumentResolverAround{
    
    @Override
    public void resolveBefore(String model, BaseQueryParam queryParam) {
        
    }

    @Override
    public void resolveAfter(String model, BaseQueryParam queryParam) {

    }

    @Override
    public boolean support(BaseQueryParam queryParam) {
        return true;
    }
}
