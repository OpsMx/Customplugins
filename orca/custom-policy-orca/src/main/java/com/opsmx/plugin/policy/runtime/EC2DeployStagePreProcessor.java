package com.opsmx.plugin.policy.runtime;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.pf4j.Extension;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import org.springframework.context.annotation.ComponentScan;
import com.opsmx.plugin.policy.runtime.PolicyAgentValidationTask;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@Extension
@Component
@ComponentScan("com.opsmx.plugin.policy.runtime")
class EC2DeployStagePreProcessor implements DeployStagePreProcessor, SpinnakerExtensionPoint{
    private final Logger logger = LoggerFactory.getLogger(EC2DeployStagePreProcessor.class);

    @Override
    public boolean supports(StageExecution stage) {
        StageData stageData = stage.mapTo(StageData.class);
        return stageData.getCloudProvider().equals("aws");
    }

    @Override
    public List<StageDefinition> beforeStageDefinitions(StageExecution stage) {
        logger.info("Start of the beforeStageDefinitions EC2DeployStagePreProcessor");
        logger.info("End of the beforeStageDefinitions EC2DeployStagePreProcessor");
        return Collections.emptyList();
    }
    @Override
    public List<StepDefinition> additionalSteps(StageExecution stage) {
        return List.of(new StepDefinition("SSDPolicyValidation", PolicyAgentValidationTask.class));
    }

}
