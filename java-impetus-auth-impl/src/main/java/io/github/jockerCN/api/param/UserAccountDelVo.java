package io.github.jockerCN.api.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class UserAccountDelVo {

    @NotNull(message = "用户ID不能为空")
    private Long id;
}
