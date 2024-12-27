package io.github.jockerCN.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */

@Component
public class EventPush {

    private static ApplicationEventPublisher EVENT_PUBLISHER;

    public EventPush( ApplicationEventPublisher eventPublisher) {
        EVENT_PUBLISHER = eventPublisher;
    }

    public static void push(Object o) {
        EVENT_PUBLISHER.publishEvent(new GenericEvent(new Object(), o));
    }
}
