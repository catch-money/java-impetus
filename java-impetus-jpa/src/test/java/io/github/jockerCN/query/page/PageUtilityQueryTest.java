package io.github.jockerCN.query.page;

import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.paging.PageParam;
import io.github.jockerCN.jpa.paging.PageUtils;
import io.github.jockerCN.jpa.utils.JpaRepositoryUtils;
import io.github.jockerCN.query.QueryAnnotationTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.PageImpl;

import java.util.List;

@TestConfiguration
public class PageUtilityQueryTest implements QueryAnnotationTest {

    @Override
    public void run() {
        CustomPageParam param = new CustomPageParam();
        param.id = 1;
        param.page = 0;
        param.pageSize = 2;

        PageImpl<PayEntity> page = PageUtils.page(param);
        asserts(page.getContent().size() == 1 && page.getTotalElements() == 1,
                "PageUtils uses the query parameter page size");

        List<PayEntity> explicit = JpaRepositoryUtils.queryListPage(param, PayEntity.class, 1);
        asserts(explicit.size() == 1 && Long.valueOf(1L).equals(explicit.getFirst().getId()),
                "queryListPage accepts an explicit result type");
        asserts(param.page == 0 && param.pageSize == 2,
                "queryListPage restores the original paging values");

        List<PayEntity> implicit = JpaRepositoryUtils.queryListPage(param, 1);
        asserts(implicit.size() == 1 && Long.valueOf(1L).equals(implicit.getFirst().getId()),
                "queryListPage uses the entity type");
        asserts(param.page == 0 && param.pageSize == 2,
                "entity queryListPage also restores the original paging values");
    }

    public static class CustomBase {
    }

    @JpaQuery(PayEntity.class)
    public static class CustomPageParam extends CustomBase implements PageParam {

        @Equals
        private Integer id;

        @Page
        private Integer page;

        @PageSize
        private Integer pageSize;

        @Override
        public Integer getPage() {
            return page;
        }

        @Override
        public Integer getPageSize() {
            return pageSize;
        }

        @Override
        public void setPage(Integer page) {
            this.page = page;
        }

        @Override
        public void setPageSize(Integer pageSize) {
            this.pageSize = pageSize;
        }
    }
}
