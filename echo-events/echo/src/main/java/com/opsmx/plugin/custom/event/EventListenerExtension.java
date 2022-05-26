package com.opsmx.plugin.custom.event;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.kork.plugins.api.spring.ExposeToApp;
import com.opsmx.plugin.custom.event.config.CamelConfig;
import com.opsmx.plugin.custom.event.constants.EchoConstant;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.Map;

@Primary
@Component
@Extension
@ExposeToApp
@ConditionalOnBean({CamelConfig.class})
public class EventListenerExtension implements EventListener {

    private static ObjectMapper mapper = new ObjectMapper();

    private final Logger logger = LoggerFactory.getLogger(EventListenerExtension.class);

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Override
    public void processEvent(Event event) {
        try {
            if (camelContext.getRoute(EchoConstant.eventQueueId)!=null) {
                logger.info("Event received : {}", event);
                Map<String, Object> eventMap = mapper.convertValue(event, Map.class);
                eventMap.put("content", mapper.writeValueAsString(eventMap.get("content")));
                eventMap.put("details", mapper.writeValueAsString(eventMap.get("details")));

                String message = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(eventMap);
                producerTemplate.sendBody(EchoConstant.echoEventDirectEndPointUrl, message);
            }
        }catch (Exception e){
            logger.error("Exception occurred while processing event : {}", e);

        }
    }
}
