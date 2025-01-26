package io.github.jockerCN.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class EventPush {

    private static ApplicationEventPublisher EVENT_PUBLISHER;

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        EVENT_PUBLISHER = eventPublisher;
    }

    public static void push(Object o) {
        EVENT_PUBLISHER.publishEvent(new GenericEvent(new Object(), o));
    }
}
