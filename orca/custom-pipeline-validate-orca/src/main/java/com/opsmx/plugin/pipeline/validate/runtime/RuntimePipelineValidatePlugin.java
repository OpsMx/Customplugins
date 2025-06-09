package com.opsmx.plugin.pipeline.validate.runtime;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimePipelineValidatePlugin extends Plugin {

    private final Logger log = LoggerFactory.getLogger(getClass());
  	
    public RuntimePipelineValidatePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    public void start() {
        log.info("Runtime Pipeline Validate plugin start()");
    }

    public void stop() {
        log.info("Runtime Pipeline Validate plugin stop()");
    }
}