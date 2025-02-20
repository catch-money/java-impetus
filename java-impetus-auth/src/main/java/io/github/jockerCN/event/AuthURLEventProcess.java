package io.github.jockerCN.event;


import io.github.jockerCN.permissions.AuthUrlProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@Slf4j
@Component
public class AuthURLEventProcess implements EventProcess {


    @Override
    public void process(Object source) {
        log.info("### AuthURLEventProcess#process auth url event ###");
        AuthUrlProcess.getInstance().refreshPermissions();
    }

    @Override
    public boolean isProcess(Object source) {
        return source instanceof AuthURLEvent;
    }
}
