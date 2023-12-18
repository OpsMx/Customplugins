package com.opsmx.plugin.stage.custom.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ToString
@Configuration
@ConfigurationProperties("policy.opa")
public class OpaConfigProperties {
    private String opaUrl="http://opa:8181/v1/data";
    private String opaResultKey="deny";
    private boolean isOpaEnabled=false;
    private boolean isOpaProxy=true;
    private boolean deltaVerification=false;
}
