package io.github.jockerCN.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GenericEventListener  {

    @Autowired
    private ApplicationContext applicationContext;

    @EventListener
    public void handleGenericEvent(GenericEvent event) {
        Map<String, EventProcess> processMap = applicationContext.getBeansOfType(EventProcess.class);
        for (Map.Entry<String, EventProcess> eventProcessEntry : processMap.entrySet()) {
            if (eventProcessEntry.getValue().isProcess(event.getEventData())) {
                eventProcessEntry.getValue().process(event.getEventData());
                return;
            }
        }

    }
}