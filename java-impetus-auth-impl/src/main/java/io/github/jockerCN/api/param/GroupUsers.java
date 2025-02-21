package io.github.jockerCN.api.param;

import lombok.Builder;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@Builder
public class GroupUsers {

    private String userCode;

    private String username;
}
