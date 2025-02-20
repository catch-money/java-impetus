package io.github.jockerCN.event;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Data
@Builder
public class GroupPermissionEvent {


    private Set<String> groupIds;
}
