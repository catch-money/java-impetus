package io.github.jockerCN.event;


import io.github.jockerCN.permissions.GroupPermissionsProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@Component
public class GroupPermissionEventProcess implements EventProcess {


    @Override
    public void process(Object source) {
        GroupPermissionEvent event = (GroupPermissionEvent) source;
        log.info("### GroupPermissionEventProcess#process group permission event ###");
        GroupPermissionsProcess.getInstance().refreshGroupPermissions(event.getGroupIds());
    }

    @Override
    public boolean isProcess(Object source) {
        return source instanceof GroupPermissionEvent;
    }
}
