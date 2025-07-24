package jockerCN.web.page.queryParam;

import io.github.jockerCN.customize.QueryPair;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TestQueryParam extends BaseQueryParam {


    private QueryPair<Integer> queryPair;


    private LocalDate localDate;


    private QueryPair<LocalDate> localDatePair;
}
