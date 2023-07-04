package com.opsmx.plugin.custom.event;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.kork.plugins.api.spring.ExposeToApp;
import com.opsmx.plugin.custom.event.config.CamelConfig;
import com.opsmx.plugin.custom.event.config.SpinnakerConfig;
import com.opsmx.plugin.custom.event.config.SsdConfig;
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
import org.springframework.util.StringUtils;

import java.util.*;

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

    @Autowired
    private SsdConfig ssdConfig;

    @Autowired
    private SpinnakerConfig spinnakerConfig;

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

                if (ssdConfig.isEnable()) {
                    eventMap.put("spinnakerName", spinnakerConfig.getName());
                    ssdEvents(eventMap);
                }
            }
        } catch (Exception e) {
            logger.error("Exception occurred while processing event : {}", e);
        }
    }

    private void ssdEvents(Map<String, Object> eventMap) {
        boolean pipelineStatus = false;
        try {
            Map<String, Object> details = mapper.readValue(eventMap.get("details").toString(), new TypeReference<>() {});
            if (details.containsKey("type") && details.get("type") != null &&
                    (details.get("type").toString().equals("orca:pipeline:complete") || details.get("type").toString().equals("orca:pipeline:failed"))) {
                pipelineStatus = true;
            }
            Map<String, Object> content = mapper.readValue(eventMap.get("content").toString(), new TypeReference<>() {});
            if (content.containsKey("execution") && content.get("execution") != null) {
                LinkedHashMap execution = (LinkedHashMap) content.get("execution");
                if (execution.containsKey("stages") && execution.get("stages") != null) {
                    ArrayList stages = (ArrayList) execution.get("stages");
                    String ssdMessage = "";
                    for (Object stage : stages) {
                        Map<String, Object> stageMap = mapper.convertValue(stage, Map.class);
                        if (pipelineStatus && stageMap.containsKey("type") && stageMap.get("type").toString().trim().equals("deployManifest")
                                && stageMap.containsKey("status") && stageMap.get("status").toString().trim().equals("SUCCEEDED")) {
                            ssdMessage = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(eventMap);
                        }
                    }
                    if (!StringUtils.isEmpty(ssdMessage)) {
                        producerTemplate.sendBody(EchoConstant.echoEventDirectEndPointUrlForSSD, ssdMessage);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            logger.error("Exception occurred while processing event : {}", e);
        }
    }
}
