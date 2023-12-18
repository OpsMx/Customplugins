package com.opsmx.plugin.stage.custom.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
@Configuration
@ConfigurationProperties(prefix = "policy.opa")
public class OpaConfigProperties {
    private String url="http://opa:8181/v1/data";
    private String resultKey="deny";
    private boolean enabled=false;
    private boolean proxy=true;
    private boolean deltaVerification=false;
    private List<Policy> policyList = new ArrayList<>();
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "policy.opa.policies")
    public static class Policy{
     private String name;
     private String packageName;
 }
}
