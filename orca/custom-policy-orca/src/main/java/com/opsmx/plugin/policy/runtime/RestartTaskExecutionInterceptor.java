package com.opsmx.plugin.policy.runtime;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskExecutionInterceptor;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class RestartTaskExecutionInterceptor implements TaskExecutionInterceptor {

    private final Logger logger = LoggerFactory.getLogger(RestartTaskExecutionInterceptor.class);

    private ValidationRestartPipeline restartPipelineValidationTask;

    public RestartTaskExecutionInterceptor(ValidationRestartPipeline restartPipelineValidationTask) {
        this.restartPipelineValidationTask = restartPipelineValidationTask;
    }

    @Override
    public StageExecution beforeTaskExecution(Task task, StageExecution stage) {
        logger.debug("Start of the beforeTaskExecution RestartTaskExecutionInterceptor");
        logger.debug("stage type :{}",stage.getType());
        logger.debug("stage tasks :{}",stage.getTasks());
        List<TaskExecution> taskExecutions = stage.getTasks();
        taskExecutions.stream().forEach(taskExecution -> { logger.debug("Task Execution :{}",taskExecution.getName());});
        if (stage.getExecution()!=null && stage.getContext().containsKey("restartDetails")){
            logger.info("Stage is being restarted, stage type : {}",stage.getType());
               if(isValidStageType(stage.getType()) && !isRepeating(stage.getContext())){
                restartPipelineValidationTask.execute(stage);
            }
        }

        logger.debug("End of the beforeTaskExecution RestartTaskExecutionInterceptor");
        return stage;
    }

    private boolean isRepeating(Map<String, Object> context){
        long restartTime = Long.valueOf(String.valueOf(((Map)context.get("restartDetails")).get("restartTime")));
        long systemTime = System.currentTimeMillis();
        logger.debug("restartTime :{}, systemTime :{} , diff :{}",restartTime, systemTime, (systemTime-restartTime));
        //ManualJudgmentTask and MonitorPipelineTask using 15 seconds to repeat task execution (backoffPeriod)
         if((systemTime-restartTime) > 15000){
             return true;
         }
        return false;
    }
    private boolean isValidStageType(String stageType){
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
        } else if (stageType.equalsIgnoreCase("jenkins")) {
            return true;
        }
        return false;
    }
}
