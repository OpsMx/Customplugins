package com.opsmx.plugin.pipeline.runtime.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "policy.opa")
@EnableConfigurationProperties({OpaConfigProperties.class, OpaConfigProperties.RuntimePolicy.class, OpaConfigProperties.ManualJudgmentPolicy.class})
public class OpaConfigProperties {
    private String url="http://oes-sapor:8085";
    private String runtimeUrl="http://opa:8181";
    private String opaPolicyLocation = "/v1/data";
    private String resultKey="deny";
    private boolean enabled=false;
    private RuntimePolicy runtime = new RuntimePolicy();
    private ManualJudgmentPolicy manualjudgment = new ManualJudgmentPolicy();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRuntimeUrl() {
        return runtimeUrl;
    }

    public void setRuntimeUrl(String runtimeUrl) {
        this.runtimeUrl = runtimeUrl;
    }

    public String getOpaPolicyLocation() {
        return opaPolicyLocation;
    }

    public void setOpaPolicyLocation(String opaPolicyLocation) {
        this.opaPolicyLocation = opaPolicyLocation;
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

    public RuntimePolicy getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimePolicy runtime) {
        this.runtime = runtime;
    }

    public ManualJudgmentPolicy getManualjudgment() {
        return manualjudgment;
    }

    public void setManualjudgment(ManualJudgmentPolicy manualjudgment) {
        this.manualjudgment = manualjudgment;
    }

    @Configuration
    @ConfigurationProperties(prefix = "policy.opa.runtime")
    public static class RuntimePolicy {
        private boolean enabled = false;
        private List<Policy> policies;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Policy> getPolicies() {
            return policies;
        }

        public void setPolicies(List<Policy> policies) {
            this.policies = policies;
        }
    }
    @Configuration
    @ConfigurationProperties(prefix = "policy.opa.manualjudgment")
    public static class ManualJudgmentPolicy {
        private boolean enabled = false;
        private List<Policy> policies;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Policy> getPolicies() {
            return policies;
        }

        public void setPolicies(List<Policy> policies) {
            this.policies = policies;
        }
    }
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
