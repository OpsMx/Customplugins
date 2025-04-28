package com.opsmx.plugin.policy.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.echo.pipeline.ManualJudgmentStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EC2DeployStagePreProcessor implements DeployStagePreProcessor{
    private final Logger logger = LoggerFactory.getLogger(EC2DeployStagePreProcessor.class);

    @Override
    boolean supports(StageExecution stage) {
        return stage.getType() == "deploy"; // && stageData.useSourceCapacity
    }

    @Override
    List<StageDefinition> beforeStageDefinitions(StageExecution stage) {
        logger.info("Start of the beforeStageDefinitions EC2DeployStagePreProcessor");
        logger.info("End of the beforeStageDefinitions EC2DeployStagePreProcessor");
        return null;
    }

}
