package io.github.jockerCN.page;

import io.github.jockerCN.Result;
import io.github.jockerCN.jpa.paging.PageParam;
import io.github.jockerCN.jpa.paging.PageUtils;
import jakarta.validation.Valid;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One endpoint for all explicitly mapped modules. Business hooks belong to JPA. */
@RestController
@RequestMapping("/module")
public class PageController {

    public PageController() {
        LoggerFactory.getLogger(PageController.class).info("### PageController#init ###");
    }

    @GetMapping("/page")
    public Result<PageImpl<?>> page(@Valid @ModulePageParam PageParam queryParam) {
        return Result.ok(PageUtils.page(queryParam));
    }
}
