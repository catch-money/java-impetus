package io.github.jockerCN.page;


import io.github.jockerCN.Result;
import io.github.jockerCN.jpa.pojo.BaseQueryParam;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@RequestMapping("/module")
@RestController
public class PageController {


    @GetMapping("page")
    public Result<PageImpl<?>> page(@ModulePageParam BaseQueryParam queryParam) {
        return Result.ok(PageUtils.page(queryParam));
    }
}
