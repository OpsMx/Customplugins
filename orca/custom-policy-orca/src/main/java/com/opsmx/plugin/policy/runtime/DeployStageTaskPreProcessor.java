package com.opsmx.plugin.policy.runtime;

import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskExecutionInterceptor;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@ConfigurationProperties(prefix = "ssd.validation")
public class DeployStageTaskPreProcessor implements TaskExecutionInterceptor {

    private final Logger logger = LoggerFactory.getLogger(DeployStageTaskPreProcessor.class);

    public DeployStageTaskPreProcessor() { }
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        logger.info("SSD url :{}",url);
        this.url = url;
    }
    private String url="https://ssd.poc.opsmx.net/ssdservice/v1/ssdFirewall";
    @Override
    public StageExecution beforeTaskExecution(Task task, StageExecution stage) {
        if(!isValidStageType(stage.getType())){
            return stage;
        }
        logger.debug("Start of the beforeTaskExecution DeployStageTaskPreProcessor");
        logger.debug("stage type :{}",stage.getType());
        if (stage.getExecution()!=null && stage.getExecution().getCancellationReason() != null ){
            logger.debug("CancellationReason :{} ",stage.getExecution().getCancellationReason());
            List<TaskExecution> taskExecutions = stage.getTasks();
            taskExecutions.stream().forEach(taskExecution -> {
                logger.debug("Task Execution :{}",taskExecution.getName());
                taskExecution.setStatus(ExecutionStatus.TERMINAL);
                taskExecution.setEndTime(System.currentTimeMillis());
            });
            stage.setStatus(ExecutionStatus.TERMINAL);
            stage.getExecution().setStatus(ExecutionStatus.TERMINAL);
            setCancelReason(stage);
            stage.getContext().put("errors", stage.getExecution().getCancellationReason());
            return stage;
        }
        if (!validateDeployment(stage)){
            throw new ValidationException("ERROR: This artifact deployment is blocked by SSD", null);
        }


        logger.debug("End of the beforeTaskExecution DeployStageTaskPreProcessor");
        return stage;
    }

    private void setCancelReason(StageExecution stage) {
        List<String> errors =new ArrayList<>();
        errors.add(stage.getExecution().getCancellationReason());
        stage.getExecution().getContext().put("exception", new HashMap<>().put("details", new HashMap<>().put("errors",errors)));
    }
    private boolean isValidStageType(String stageType){
        if (stageType.equalsIgnoreCase("deployManifest")) {
            return true;
        }
        return false;
    }
    private boolean validateDeployment(StageExecution stage){
        boolean reply = false;
        String application = stage.getExecution().getApplication();
        String pipelineName = stage.getExecution().getName();
        Map<String, Object> stageContext = stage.getContext();
        logger.debug("Stage Context :{}", stageContext);
        String account = stageContext.get("account").toString();
        String artifact = getArtifact(stageContext);
        String requestBody = "{\"account\": \"" + account + "\", \"appName\": \"" + application + "\", \"artifact\": \"" + artifact + "\",  \"service\": \"" + pipelineName + "\"}";
        logger.debug("!!!!!!!!! Inside DeployStageTaskPreProcessor validateDeployment   " + requestBody);

        OkHttpClient ssdClient = new OkHttpClient();
        RequestBody body = RequestBody.create(requestBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = ssdClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.debug("Request failed: " + response.code());
                return false;
            }

            String responseBody = response.body().string();
            String regex = ".*allow.?.?:.?.?true[,}]?.*";
            logger.debug("!!!!!!!!! Inside DeployStageTaskPreProcessor validateDeployment responseBody " + responseBody);

            // Check if "allow" is true
            if (responseBody.matches(regex)) {
                logger.debug("Access is allowed.");
                reply = true;
            } else {
                logger.debug("Access is denied.");
                reply = false;
            }

        } catch (IOException e) {
            logger.error("Exception: error occur while calling ssd :{}",e);
        }
        return reply;

    }

    private String getArtifact(Map<String, Object> stageContext) {
        logger.debug("Start of the getArtifact DeployStageTaskPreProcessor");
        List manifests = (List) stageContext.get("manifests");
        Map<String, Object> manifest = (Map<String, Object>) manifests.get(0);
        Map<String, Object> spec = (Map<String, Object>) manifest.get("spec");
        Map<String, Object> template = (Map<String, Object>) spec.get("template");
        Map<String, Object> templateSpec = (Map<String, Object>) template.get("spec");
        List containers = (List) templateSpec.get("containers");
        Map<String, Object>  container = (Map<String, Object>) containers.get(0);
        logger.debug("End of the getArtifact DeployStageTaskPreProcessor");
        return container.get("image").toString();
    }
}