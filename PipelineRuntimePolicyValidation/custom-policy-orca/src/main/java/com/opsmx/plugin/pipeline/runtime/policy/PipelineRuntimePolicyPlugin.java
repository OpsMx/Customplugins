package com.opsmx.plugin.pipeline.runtime.policy;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipelineRuntimePolicyPlugin extends Plugin {

    private final Logger log = LoggerFactory.getLogger(getClass());
  	
    public PipelineRuntimePolicyPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    public void start() {
        log.info("Pipeline Runtime Policy plugin start()");
    }

    public void stop() {
        log.info("Pipeline Runtime Policy plugin stop()");
    }
}