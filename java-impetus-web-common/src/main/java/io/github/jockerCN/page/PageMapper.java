package io.github.jockerCN.page;

import io.github.jockerCN.jpa.pojo.BaseQueryParam;

import java.util.Map;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

public interface PageMapper {


    Map<String, Class<? extends BaseQueryParam>> getQueryParamClassMap();



    static PageMapper defaultEmptyPageMapper() {
        return Map::of;
    }
}
