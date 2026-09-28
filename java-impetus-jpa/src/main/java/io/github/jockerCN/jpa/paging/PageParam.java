package io.github.jockerCN.jpa.paging;

/** Access to pagination values for paging convenience methods. */
public interface PageParam {

    Integer getPage();

    Integer getPageSize();

    void setPage(Integer page);

    void setPageSize(Integer pageSize);
}
