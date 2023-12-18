package com.opsmx.plugin.policy.runtime;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimePolicyPlugin extends Plugin {

    private final Logger log = LoggerFactory.getLogger(getClass());
  	
    public RuntimePolicyPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    public void start() {
        log.info("Runtime Policy plugin start()");
    }

    public void stop() {
        log.info("Runtime Policy plugin stop()");
    }
}