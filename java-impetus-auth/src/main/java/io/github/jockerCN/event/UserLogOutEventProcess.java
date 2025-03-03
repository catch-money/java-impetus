package io.github.jockerCN.event;


import io.github.jockerCN.permissions.UserPermissionsProcess;
import io.github.jockerCN.token.process.TokenRecordProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@Component
public class UserLogOutEventProcess implements EventProcess {


    @Override
    public void process(Object source) {
        log.info("### UserAccountEventProcess#process user logout event ###");
        UserLogOutEvent userAccountEvent = (UserLogOutEvent) source;
        TokenRecordProcess.getInstance().clearTokenInfo(userAccountEvent.getUserCode());
        UserPermissionsProcess.getInstance().remove(userAccountEvent.getUserCode());
    }

    @Override
    public boolean isProcess(Object source) {
        return source instanceof UserLogOutEvent;
    }
}
