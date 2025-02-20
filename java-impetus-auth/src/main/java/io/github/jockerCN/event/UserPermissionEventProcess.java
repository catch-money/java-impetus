package io.github.jockerCN.event;


import io.github.jockerCN.permissions.UserPermissionsProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@Component
public class UserPermissionEventProcess implements EventProcess {


    @Override
    public void process(Object source) {
        UserPermissionEvent userPermissionEvent = (UserPermissionEvent) source;
        final String userCode = userPermissionEvent.getUserCode();
        log.info("### UserPermissionEventProcess#process userCode: [{}]", userCode);
        UserPermissionsProcess.getInstance().aggregate(userCode);
    }

    @Override
    public boolean isProcess(Object source) {
        return source instanceof UserPermissionEvent;
    }
}
