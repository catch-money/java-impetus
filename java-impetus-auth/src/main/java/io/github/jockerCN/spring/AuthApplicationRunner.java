package io.github.jockerCN.spring;

import io.github.jockerCN.event.AuthURLEvent;
import io.github.jockerCN.event.EventPush;
import io.github.jockerCN.event.GroupPermissionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@SuppressWarnings("unused")
@Slf4j
public abstract class AuthApplicationRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("### AuthApplicationRunner#run start");

        log.info("### publish event GroupPermissionEvent ###");
        EventPush.push(GroupPermissionEvent.builder().build());

        log.info("### publish event AuthURLEvent ###");
        EventPush.push(AuthURLEvent.builder().build());
    }

}
