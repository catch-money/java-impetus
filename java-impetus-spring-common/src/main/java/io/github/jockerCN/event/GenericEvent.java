package io.github.jockerCN.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GenericEvent extends ApplicationEvent {

    private final Object eventData;

    public GenericEvent(Object source, Object eventData) {
        super(source);
        this.eventData = eventData;
    }

}
