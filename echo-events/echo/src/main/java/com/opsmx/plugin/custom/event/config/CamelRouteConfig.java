package com.opsmx.plugin.custom.event.config;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;

public interface CamelRouteConfig {

    default void initRequiredValues(){}
    String configure();
    String configureISDRoute();
    String ssdConfigure();

    @Retryable(value = {IOException.class, InterruptedException.class}, backoff = @Backoff(delay = 3000))
    void deleteRoute(String name) throws Exception;
}
