package io.github.jockerCN.page;

import io.github.jockerCN.common.SpringProvider;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import org.springframework.data.domain.PageImpl;

import java.util.Collection;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public interface PageResultProcess {


    boolean support(BaseQueryParam queryParam);


    void process(PageImpl<?> page);

    DefaultPageResultProcess DEFAULT_PAGE_RESULT_PROCESS = new DefaultPageResultProcess();

    static PageResultProcess getInstance(BaseQueryParam queryParam){
        Collection<PageResultProcess> processes = SpringProvider.getBeans(PageResultProcess.class);

        for (PageResultProcess process : processes) {
            if (process.support(queryParam)){
                return process;
            }
        }
        return DEFAULT_PAGE_RESULT_PROCESS;
    }

    class DefaultPageResultProcess implements PageResultProcess {

        @Override
        public boolean support(BaseQueryParam queryParam) {
            return false;
        }

        @Override
        public void process(PageImpl<?> page) {

        }
    }

}
