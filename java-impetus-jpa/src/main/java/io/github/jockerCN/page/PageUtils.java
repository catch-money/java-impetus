package io.github.jockerCN.page;


import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class PageUtils {

    private static final JpaQueryManager jpaQueryManager = SpringProvider.getBean(JpaQueryManager.class);

    public static <T> PageImpl<T> page(BaseQueryParam queryParam) {
        List<T> queryList = jpaQueryManager.queryList(queryParam);
        if (CollectionUtils.isEmpty(queryList)) {
            return new PageImpl<>(queryList, Pageable.unpaged(), 0);
        }
        Long count = jpaQueryManager.count(queryParam);
        return new PageImpl<>(queryList, Pageable.unpaged(), count);
    }
}
