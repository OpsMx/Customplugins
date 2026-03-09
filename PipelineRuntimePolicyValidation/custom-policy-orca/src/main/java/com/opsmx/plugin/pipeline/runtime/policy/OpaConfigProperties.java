package com.opsmx.plugin.pipeline.runtime.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties({OpaConfigProperties.class})
public class OpaConfigProperties {
    private String isdUrl="http://oes-sapor:8085/v1/data";
    private String resultKey="deny";
    private boolean enabled=false;
    private List<Policy> runtime;

    public String getIsdUrl() {
        return isdUrl;
    }

    public void setIsdUrl(String isdUrl) {
        this.isdUrl = isdUrl;
    }

    public String getResultKey() {
        return resultKey;
    }

    public void setResultKey(String resultKey) {
        this.resultKey = resultKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Policy> getRuntime() {
        return runtime;
    }

    public void setRuntime(List<Policy> runtime) {
        this.runtime = runtime;
    }

    @Configuration
    @ConfigurationProperties(prefix = "policy.opa.runtime")
    public static class Policy{
        private String name;
        private String packageName;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }
    }
}
