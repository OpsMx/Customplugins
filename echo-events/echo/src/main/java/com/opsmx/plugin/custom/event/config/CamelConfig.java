package com.opsmx.plugin.custom.event.config;

import com.netflix.spinnaker.kork.plugins.api.spring.ExposeToApp;
import com.opsmx.plugin.custom.event.ISDEvent;
import com.opsmx.plugin.custom.event.constants.EchoConstant;
import com.opsmx.plugin.custom.event.model.CDRouteInfo;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@ExposeToApp
@Configuration
@ConditionalOnExpression("${message-broker.enabled:true}")
public class CamelConfig {

    @Autowired
    private CamelRouteConfiguration camelRouteConfiguration;

    @Bean
    @ExposeToApp
    public CamelContext camelContext() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addRoutes(camelRouteConfiguration);
        camelContext.getShutdownStrategy().setShutdownNowOnTimeout(true);
        camelContext.getShutdownStrategy().setTimeout(5);
        camelContext.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        camelContext.start();
        return camelContext;
    }

    @Bean
    @ExposeToApp
    public ProducerTemplate producerTemplate(CamelContext camelContext){
        return camelContext.createProducerTemplate();
    }

    @ExposeToApp
    @Configuration
    @ConditionalOnExpression("${message-broker.enabled:true}")
    public static class CamelRouteConfiguration extends RouteBuilder{

        @Autowired
        private CamelRouteConfig camelRouteConfig;

        @Autowired
        private ISDEvent isdEvent;

        @Override
        public void configure() throws Exception {
            camelRouteConfig.initRequiredValues();
            from(EchoConstant.echoEventDirectEndPointUrl).id(EchoConstant.eventQueueId)
                    .to(camelRouteConfig.configure())
                    .end();

            from(camelRouteConfig.configureISDRoute())
                    .id(EchoConstant.isdToSpinQueue)
                    .unmarshal().json(CDRouteInfo.class)
                    .bean(isdEvent, "handleEvent")
                    .end();

            from(EchoConstant.echoEventDirectEndPointUrlForSSD).id(EchoConstant.eventQueueIdForSSD)
                    .to(camelRouteConfig.ssdConfigure())
                    .end();
        }
    }

}
