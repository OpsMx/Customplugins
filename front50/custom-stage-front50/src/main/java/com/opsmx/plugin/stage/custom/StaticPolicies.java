package com.opsmx.plugin.stage.custom;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@EnableConfigurationProperties({StaticPolicies.class, StaticPolicies.Policy.class})
public class StaticPolicies {
    private List<Policy> policyList;
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "policy.opa.static")
    public static class Policy {
        private String name;
        private String packageName;
    }
}