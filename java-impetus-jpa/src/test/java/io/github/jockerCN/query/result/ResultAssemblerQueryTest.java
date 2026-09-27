package io.github.jockerCN.query.result;

import io.github.jockerCN.entity.PayEntity;
import io.github.jockerCN.jpa.JpaQueryManager;
import io.github.jockerCN.jpa.annotation.Columns;
import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.where.Equals;
import io.github.jockerCN.jpa.query.model.SelectColumn;
import io.github.jockerCN.jpa.query.result.ResultAssembler;
import io.github.jockerCN.query.QueryAnnotationTest;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@TestConfiguration
public class ResultAssemblerQueryTest implements QueryAnnotationTest {

    @Autowired
    private JpaQueryManager queryManager;

    @Override
    public void run() {
        PayQueryParam param = new PayQueryParam();
        param.id = 1;
        param.columns = List.of(SelectColumn.of("id"), SelectColumn.of("customerPhone"));
        ResultAssembler<Tuple, PayView> beanAssembler = ResultAssembler.bean(PayView.class);

        PayView full = queryManager.query(param, beanAssembler);
        asserts(full != null && full.getId() == 1L && "13725090127".equals(full.getCustomerPhone()),
                "ResultAssembler full Tuple mapping");
        List<PayView> fullList = queryManager.queryList(param, beanAssembler);
        asserts(fullList.size() == 1 && "13725090127".equals(fullList.getFirst().getCustomerPhone()),
                "ResultAssembler bound full Tuple mapping");

        param.columns = List.of(SelectColumn.of("id"));
        List<PayView> partial = queryManager.queryList(param, beanAssembler);
        asserts(partial.size() == 1 && partial.getFirst().getId() == 1L
                && partial.getFirst().getCustomerPhone() == null, "ResultAssembler dynamic columns");

        param.columns = List.of(SelectColumn.of("id"), SelectColumn.nullValue("customerPhone", String.class));
        PayView nullColumn = queryManager.query(param, beanAssembler);
        asserts(nullColumn != null && nullColumn.getId() == 1L && nullColumn.getCustomerPhone() == null,
                "ResultAssembler selected null column");

        param.columns = List.of(SelectColumn.of("id"));

        PayView custom = queryManager.query(param, Tuple.class, (sameParam, row) -> {
            asserts(sameParam == param, "ResultAssembler original queryParam");
            PayView view = new PayView();
            view.setId(row.get("id", Long.class));
            view.setCustomerPhone("custom");
            return view;
        });
        asserts(custom != null && "custom".equals(custom.getCustomerPhone()),
                "ResultAssembler custom mapping");

        PayView arrayResult = queryManager.query(param, Object[].class, (sameParam, row) -> {
            PayView view = new PayView();
            view.setId((Long) row[0]);
            return view;
        });
        asserts(arrayResult != null && arrayResult.getId() == 1L,
                "ResultAssembler dynamic native result type");

        param.id = -1;
        asserts(queryManager.query(param, beanAssembler) == null,
                "ResultAssembler absent single result");
        asserts(queryManager.queryList(param, beanAssembler).isEmpty(),
                "ResultAssembler empty list");

        AtomicInteger bindings = new AtomicInteger();
        ResultAssembler<Tuple, Long> trackingAssembler = new ResultAssembler<>() {
            @Override
            public Long assemble(Object queryParam, Tuple row) {
                throw new AssertionError("List queries should use the bound assembler");
            }

            @Override
            public ResultAssembler<Tuple, Long> bind(Tuple sampleRow) {
                bindings.incrementAndGet();
                return (queryParam, row) -> row.get("id", Long.class);
            }
        };
        asserts(queryManager.queryList(param, trackingAssembler).isEmpty() && bindings.get() == 0,
                "ResultAssembler skips binding for an empty list");
        param.id = null;
        List<Long> ids = queryManager.queryList(param, trackingAssembler);
        asserts(ids.size() == 16 && bindings.get() == 1,
                "ResultAssembler binds the result shape once per list query");
    }

    @JpaQuery(PayEntity.class)
    public static class PayQueryParam {
        @Equals
        private Integer id;

        @Columns
        private List<SelectColumn> columns;
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
