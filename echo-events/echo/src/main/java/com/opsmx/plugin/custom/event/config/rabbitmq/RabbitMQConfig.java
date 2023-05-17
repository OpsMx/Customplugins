package com.opsmx.plugin.custom.event.config.rabbitmq;

import com.netflix.spinnaker.kork.plugins.api.spring.ExposeToApp;
import com.opsmx.plugin.custom.event.config.CamelConfig;
import com.opsmx.plugin.custom.event.config.CamelRouteConfig;
import com.opsmx.plugin.custom.event.config.MessageBrokerConfig;
import com.opsmx.plugin.custom.event.config.SpinnakerConfig;
import com.opsmx.plugin.custom.event.config.SsdConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@ExposeToApp
@Configuration("rabbitMQConfig")
@ConditionalOnBean({CamelConfig.class})
@ConditionalOnProperty(value = "message-broker.endpoint.name", havingValue = "rabbitmq")
public class RabbitMQConfig implements CamelRouteConfig {


    @Autowired
    private MessageBrokerConfig messageBrokerConfig;

    @Autowired
    private SpinnakerConfig spinnakerConfig;

    @Autowired
    private SsdConfig ssdConfig;

    private static final String exchange = "echo.events";

    @Value("${message-broker.apiProtocol:http}")
    private String apiProtocol;

    private String rmqUrl;

    private final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);


    @Override
    public void initRequiredValues() {
        rmqUrl = apiProtocol + "://" + messageBrokerConfig.getUsername() + ":" + messageBrokerConfig.getPassword() + "@" + messageBrokerConfig.getHost() + ":1" + messageBrokerConfig.getPort();
    }

    @Override
    public String configure() {

        return messageBrokerConfig.getEndpoint().getName()+":"+exchange+"?queue="
                +spinnakerConfig.getName()+"&autoDelete=false&routingKey="
                +spinnakerConfig.getName()+"&declare=false&durable=true&exchangeType=direct&hostname="
                +messageBrokerConfig.getHost() +"&portNumber="+messageBrokerConfig.getPort()
                +"&username="+messageBrokerConfig.getUsername()+"&password="+messageBrokerConfig.getPassword();
    }

    @Override
    public String configureISDRoute() {

        return messageBrokerConfig.getEndpoint().getName()+":"+exchange+"?queue="
                +"isd-to-"+spinnakerConfig.getName()+"&autoDelete=false&routingKey="
                +"isd-to-"+spinnakerConfig.getName()+"&declare=false&durable=true&exchangeType=direct&hostname="
                +messageBrokerConfig.getHost() +"&portNumber="+messageBrokerConfig.getPort()
                +"&username="+messageBrokerConfig.getUsername()+"&password="+messageBrokerConfig.getPassword();
    }

    @Override
    public void deleteRoute(String name) throws IOException, InterruptedException {

        Process process = null;
        try {
            logger.info("deleting the queue : {}", name);
            String deleteUrl = rmqUrl + "/api/queues/%2f/" + name;
            String cmd = "curl -X DELETE " + deleteUrl;
            process = Runtime.getRuntime().exec(cmd);
            process.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e){
            logger.warn("Exception during deleting the RabbitMQ path : {}", e);
            throw e;
        }
        finally {
            if (process!=null) {
                process.destroyForcibly();
            }
        }
    }

    @Override
    public String ssdConfigure() {
        return messageBrokerConfig.getEndpoint().getName() + ":" + exchange + "?queue="
                + ssdConfig.getName() + "&autoDelete=false&routingKey="
                + ssdConfig.getName() + "&declare=true&durable=true&exchangeType=direct&hostname="
                + messageBrokerConfig.getHost() + "&portNumber=" + messageBrokerConfig.getPort()
                + "&username=" + messageBrokerConfig.getUsername() + "&password=" + messageBrokerConfig.getPassword();
    }
}
