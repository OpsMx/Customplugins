package com.opsmx.plugin.stage.custom;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RbacPlugin extends Plugin {
	
	private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Constructor to be used by plugin manager for plugin instantiation.
     * Your plugins have to provide constructor with this exact signature to
     * be successfully loaded by manager.
     *
     * @param wrapper
     */
    public RbacPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    public void start() {
        log.info("Rbac Plugin started");
    }

    public void stop() {
        log.info("Rbac Plugin stoped");
    }
}
