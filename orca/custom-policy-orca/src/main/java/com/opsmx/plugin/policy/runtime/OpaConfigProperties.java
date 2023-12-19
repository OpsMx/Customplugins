package com.opsmx.plugin.policy.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "policy.opa")
@EnableConfigurationProperties({OpaConfigProperties.class, OpaConfigProperties.Policy.class})
public class OpaConfigProperties {
    private String url="http://opa:8181/v1/data";
    private String resultKey="deny";
    private boolean enabled=false;
    private boolean proxy=true;
    private boolean deltaVerification=false;
    private List<Policy> runtime;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public boolean isProxy() {
        return proxy;
    }

    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    public boolean isDeltaVerification() {
        return deltaVerification;
    }

    public void setDeltaVerification(boolean deltaVerification) {
        this.deltaVerification = deltaVerification;
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
