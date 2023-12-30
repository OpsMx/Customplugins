package com.opsmx.plugin.policy.runtime;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskExecutionInterceptor;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RestartTaskExecutionInterceptor implements TaskExecutionInterceptor {

    private final Logger logger = LoggerFactory.getLogger(RestartTaskExecutionInterceptor.class);

    private RestartPipelineTask restartPipelineTask;

    public RestartTaskExecutionInterceptor(RestartPipelineTask restartPipelineTask) {
        this.restartPipelineTask = restartPipelineTask;
    }

    @Override
    public StageExecution beforeTaskExecution(Task task, StageExecution stage) {
        logger.debug("Start of the beforeTaskExecution RestartTaskExecutionInterceptor");
        logger.debug("Task Type :{}",task.aliases());
        logger.debug("stage tasks :{}",stage.getTasks());
        logger.debug("stage type :{}",stage.getType());
        List<TaskExecution> taskExecutions = stage.getTasks();
        taskExecutions.stream().forEach(taskExecution -> {logger.info("Task Execution :{}",taskExecution.getName());});
        if (stage.getExecution()!=null && isValidStageType(stage.getType())) {
            logger.info("Stage is not Manual Judgment ");
            restartPipelineTask.execute(stage);
        }

        logger.debug("End of the beforeTaskExecution RestartTaskExecutionInterceptor");
        return stage;
    }
    private boolean isValidStageType(String stageType) {
        if (stageType.equalsIgnoreCase("manualJudgment")) {
            return true;
        } else if (stageType.equalsIgnoreCase("evaluateVariables")) {
            return true;
        } else if (stageType.equalsIgnoreCase("pipeline")) {
            return true;
        } else if (stageType.equalsIgnoreCase("runJob")) {
            return true;
        } else if (stageType.equalsIgnoreCase("startScript")) {
            return true;
        }
        return false;
    }
}
