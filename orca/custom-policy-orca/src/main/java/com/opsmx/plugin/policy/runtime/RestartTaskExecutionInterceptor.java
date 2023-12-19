package com.opsmx.plugin.policy.runtime;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskExecutionInterceptor;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
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
        logger.debug("stage :{}",stage.getExecution());
        logger.debug("Cancellation Reason :{}",stage.getExecution().getCancellationReason());
        logger.debug("stage type :{}",stage.getType());
        logger.debug("stage tasks :{}",stage.getTasks());
        List<TaskExecution> taskExecutions = stage.getTasks();
        taskExecutions.stream().forEach(taskExecution -> { logger.debug("Task Execution :{}",taskExecution.getName());});
        if (stage.getExecution()!=null && stage.getContext().containsKey("restartDetails")){
            logger.info("Stage is being restarted, stage type : {}",stage.getType());
               if(isValidStageType(stage.getType()) && !isRepeating(stage.getContext())){
                restartPipelineValidationTask.execute(stage);
            }
        } else if (stage.getExecution()!=null && stage.getExecution().getCancellationReason() != null ){
            logger.info("Failed pipeline got restarted :{} ",stage.getExecution().getCancellationReason());
           // if(stage.isManualJudgmentType()){
            if(isValidStageType(stage.getType())){
                logger.info("***************************");
                taskExecutions.stream().forEach(taskExecution -> {
                    logger.debug("Task Execution :{}",taskExecution.getName());
                    taskExecution.setStatus(ExecutionStatus.TERMINAL);
                    taskExecution.setEndTime(System.currentTimeMillis());
                });
                stage.setStatus(ExecutionStatus.TERMINAL);
                stage.getExecution().setStatus(ExecutionStatus.TERMINAL);
                setCancelReason(stage);
                stage.getContext().put("errors", stage.getExecution().getCancellationReason());
            }
        }
        /*else if (stage.getExecution()!=null && stage.getContext().containsKey("failPipeline")){
            logger.info("Pipeline is being Reschedule, stage type : {}",stage.getType());
            if("manualJudgment".equalsIgnoreCase(stage.getType()) && stage.getContext().get("failPipeline").toString().equalsIgnoreCase("true")){
                restartPipelineValidationTask.execute(stage);
            }
        }*/

        logger.debug("End of the beforeTaskExecution RestartTaskExecutionInterceptor");
        return stage;
    }

    private void setCancelReason(StageExecution stage) {
        List<String> errors =new ArrayList<>();
        errors.add(stage.getExecution().getCancellationReason());
        stage.getExecution().getContext().put("exception", new HashMap<>().put("details", new HashMap<>().put("errors",errors)));
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
