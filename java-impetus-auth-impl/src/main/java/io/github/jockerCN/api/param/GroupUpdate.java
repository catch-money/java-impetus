package io.github.jockerCN.api.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class GroupUpdate {

    @NotNull(message = "id为空")
    private Long id;

    private String groupName;

    private String description;

    private int sortOrder;
}
