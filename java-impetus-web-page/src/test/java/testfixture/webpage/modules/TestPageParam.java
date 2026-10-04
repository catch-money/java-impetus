package testfixture.webpage.modules;

import io.github.jockerCN.jpa.annotation.JpaQuery;
import io.github.jockerCN.jpa.annotation.Page;
import io.github.jockerCN.jpa.annotation.PageSize;
import io.github.jockerCN.jpa.paging.PageParam;
import io.github.jockerCN.jpa.query.model.QueryPair;
import io.github.jockerCN.page.PageModule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
@PageModule("scanned")
@JpaQuery(TestPageParam.TestEntity.class)
public class TestPageParam implements PageParam {

    @Page
    @NotNull
    @Min(0)
    private Integer page = 0;
    @PageSize
    @NotNull
    @Min(1)
    private Integer pageSize = 20;
    private String name;
    private Long ownerId;
    private QueryPair<Integer> range;
    private QueryPair<LocalDate> dates;
    @DateTimeFormat(pattern = "dd_MM_uuuu")
    private QueryPair<LocalDate> formattedDates;
    private LocalDate date;
    @DateTimeFormat(pattern = "dd_MM_uuuu")
    private LocalDate formatted;
    private LocalDateTime dateTime;
    private LocalTime time;
    private OffsetDateTime offset;

    public static class TestEntity {
    }
}
