package io.github.jockerCN.page;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class SimplePageImpl<T> extends PageImpl<T> {

    public SimplePageImpl(List<T> content, Pageable pageable, long total) {
        super(content, pageable, total);
    }

    public long getTotal() {
        return super.getTotalElements();
    }
}
