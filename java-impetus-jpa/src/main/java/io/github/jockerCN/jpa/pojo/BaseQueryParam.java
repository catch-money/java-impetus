package io.github.jockerCN.jpa.pojo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public abstract class BaseQueryParam {

    private int pageSize;

    private int page;

    private int limitCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private int deleted = 1;

    private Set<String> queryColumns = new HashSet<>();

    private Set<String> descColumns = new HashSet<>();

    private Set<String> ascColumns = new HashSet<>();


    public boolean enablePage() {
        return pageSize > 0 && page >= 0;
    }

}
