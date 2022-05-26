package com.opsmx.plugin.custom.event;

import com.netflix.spinnaker.kork.plugins.api.spring.ExposeToApp;
import com.opsmx.plugin.custom.event.config.CamelRouteConfig;
import com.opsmx.plugin.custom.event.constants.EchoConstant;
import com.opsmx.plugin.custom.event.enums.Action;
import com.opsmx.plugin.custom.event.model.CDRouteInfo;
import com.opsmx.plugin.custom.event.model.Spinnaker;
import com.opsmx.plugin.custom.event.util.BeanUtil;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.stereotype.Component;

@Component
@ExposeToApp
@EnableRetry
public class ISDEvent {

    private final Logger logger = LoggerFactory.getLogger(ISDEvent.class);

    public void handleEvent(CDRouteInfo cdRouteInfo){

        logger.info("Notification received from ISD : {}", cdRouteInfo);

        try {
            switch (cdRouteInfo.getCdType()){
                case spinnaker:
                    Spinnaker spinnaker = cdRouteInfo.getCdTool();
                    handleISDEvent(spinnaker, cdRouteInfo.getAction());
                    break;
            }
        }catch (Exception e){
            logger.error("Exception occurred while handling the isd event : {}", e);
        }
    }

    private void handleISDEvent(Spinnaker spinnaker, Action action) throws Exception {
        CamelContext camelContext = BeanUtil.getBean(CamelContext.class);
        CamelRouteConfig camelRouteConfig = BeanUtil.getBean(CamelRouteConfig.class);
        switch (action){
            case remove:
                camelContext.getRouteController().stopRoute(EchoConstant.eventQueueId);
                boolean flag = camelContext.removeRoute(EchoConstant.eventQueueId);
                if (flag){
                    camelRouteConfig.deleteRoute(spinnaker.getName());
                    camelRouteConfig.deleteRoute(spinnaker.getName() + "-audit");
                    logger.info("Stopped publishing to the ISD instance");
                    camelContext.getRouteController().stopRoute(EchoConstant.isdToSpinQueue);
                    flag = camelContext.removeRoute(EchoConstant.isdToSpinQueue);
                    if (flag) {
                        camelRouteConfig.deleteRoute("isd-to-" + spinnaker.getName());
                    }
                }
                break;
        }
    }
}
