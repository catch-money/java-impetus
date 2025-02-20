package io.github.jockerCN.security.filter;

import org.springframework.core.Ordered;

public interface FilterOrder {


    int FIRST = Ordered.HIGHEST_PRECEDENCE;

    int THIRD = FIRST + 2;

    int SECOND = FIRST + 1;

    int END = Ordered.LOWEST_PRECEDENCE;

    int SECOND_LAST_PRIORITY = Ordered.LOWEST_PRECEDENCE - 1;

    int THIRD_LAST_PRIORITY = Ordered.LOWEST_PRECEDENCE - 2;


}
