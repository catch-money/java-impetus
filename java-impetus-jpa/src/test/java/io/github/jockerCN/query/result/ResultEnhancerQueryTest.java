package io.github.jockerCN.query.result;

import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.jpa.query.result.ResultAssembler;
import io.github.jockerCN.jpa.query.result.ResultEnhancer;
import io.github.jockerCN.query.QueryAnnotationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;

@TestConfiguration
public class ResultEnhancerQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager queryManager;

    @Autowired
    private EntityEnhancer entityEnhancer;

    @Autowired
    private ViewEnhancer viewEnhancer;

    @Override
    public void run() {
        EntityQueryParam entityParam = new EntityQueryParam();
        entityParam.id = 1;
        queryManager.query(entityParam);
        queryManager.queryList(entityParam);
        asserts(entityEnhancer.singleCalls == 0 && entityEnhancer.listCalls == 0,
                "ordinary query paths do not enhance results");

        PayEntity first = queryManager.queryEnhanced(entityParam);
        asserts(first != null && entityEnhancer.singleCalls == 1 && entityEnhancer.lastParam == entityParam,
                "enhanced single query receives first result and original parameter");
        List<PayEntity> oneRow = queryManager.queryListEnhanced(entityParam);
        asserts(oneRow.size() == 1 && entityEnhancer.listCalls == 1 && entityEnhancer.lastList == oneRow,
                "enhanced list query receives the whole list once");

        entityParam.id = null;
        List<PayEntity> allRows = queryManager.queryListEnhanced(entityParam, PayEntity.class);
        asserts(allRows.size() == 16 && entityEnhancer.listCalls == 2
                        && entityEnhancer.lastList == allRows,
                "enhanced list does not process rows one by one");

        entityParam.id = -1;
        asserts(queryManager.queryEnhanced(entityParam) == null && entityEnhancer.singleCalls == 1,
                "empty single query keeps null result");
        asserts(queryManager.queryListEnhanced(entityParam).isEmpty() && entityEnhancer.listCalls == 3,
                "empty list is passed to list enhancer");

        ViewQueryParam viewParam = new ViewQueryParam();
        viewParam.id = 1;
        viewParam.columns = List.of(SelectColumn.of("id"), SelectColumn.of("customerPhone"));
        ResultAssembler<jakarta.persistence.Tuple, PayView> assembler = ResultAssembler.bean(PayView.class);
        PayView plain = queryManager.query(viewParam, assembler);
        asserts(plain != null && "13725090127".equals(plain.getCustomerPhone())
                        && viewEnhancer.singleCalls == 0,
                "assembler without enhancer is unchanged");
        PayView enhanced = queryManager.queryEnhanced(viewParam, assembler);
        asserts(enhanced != null && "masked".equals(enhanced.getCustomerPhone())
                        && viewEnhancer.singleCalls == 1 && viewEnhancer.lastParam == viewParam,
                "enhancer runs after assembler for a single result");
        List<PayView> enhancedList = queryManager.queryListEnhanced(viewParam, assembler);
        asserts(enhancedList.size() == 1 && "masked-list".equals(enhancedList.getFirst().getCustomerPhone())
                        && viewEnhancer.listCalls == 1,
                "enhancer runs once on the fully assembled list");
    }

    @JpaQuery(PayEntity.class)
    public static class EntityQueryParam {
        @Equals
        private Integer id;
    }

    @JpaQuery(PayEntity.class)
    public static class ViewQueryParam {
        @Equals
        private Integer id;

        @Columns
        private List<SelectColumn> columns;
    }

    public static class EntityEnhancer implements ResultEnhancer<PayEntity> {
        @Autowired
        private JpaQueryManager queryManager;

        private int singleCalls;
        private int listCalls;
        private Object lastParam;
        private List<PayEntity> lastList;

        @Override
        public Class<?> queryParamType() {
            return EntityQueryParam.class;
        }

        @Override
        public PayEntity enhance(Object queryParam, PayEntity result) {
            if (queryManager == null) {
                throw new AssertionError("Enhancer dependency was not injected");
            }
            singleCalls++;
            lastParam = queryParam;
            return result;
        }

        @Override
        public List<PayEntity> enhanceList(Object queryParam, List<PayEntity> results) {
            listCalls++;
            lastParam = queryParam;
            lastList = results;
            return results;
        }
    }

    public static class ViewEnhancer implements ResultEnhancer<PayView> {
        private int singleCalls;
        private int listCalls;
        private Object lastParam;

        @Override
        public Class<?> queryParamType() {
            return ViewQueryParam.class;
        }

        @Override
        public PayView enhance(Object queryParam, PayView result) {
            singleCalls++;
            lastParam = queryParam;
            result.setCustomerPhone("masked");
            return result;
        }

        @Override
        public List<PayView> enhanceList(Object queryParam, List<PayView> results) {
            listCalls++;
            lastParam = queryParam;
            results.forEach(result -> result.setCustomerPhone("masked-list"));
            return results;
        }
    }

    public static class PayView {
        private Long id;
        private String customerPhone;

        public PayView() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCustomerPhone() {
            return customerPhone;
        }

        public void setCustomerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
        }
    }
}
