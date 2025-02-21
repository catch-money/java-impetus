package io.github.jockerCN.api.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
public class GroupSave {

    private Long id;

    @NotBlank(message = "组名为空")
    private String groupName;

    @NotBlank(message = "描述为空")
    private String description;

    private int sortOrder;
}
