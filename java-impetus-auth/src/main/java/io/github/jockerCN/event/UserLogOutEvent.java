package io.github.jockerCN.event;

import lombok.Builder;
import lombok.Data;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@Builder
public class UserLogOutEvent {

    private String userCode;
}
