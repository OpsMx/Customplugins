package com.opsmx.plugin.policy.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.springframework.boot.context.properties.ConfigurationProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

@Component
@ConfigurationProperties(prefix = "ssd.validation")
class PolicyAgentValidationTask implements Task {
    private final Logger logger = LoggerFactory.getLogger(PolicyAgentValidationTask.class);
    private String responseCode;
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        logger.info("SSD url :{}",url);
        this.url = url;
    }

    private String url="https://ssd.poc.opsmx.net/ssdservice/v1/ssdFirewall";

    @Nonnull
    @Override
   public TaskResult execute(@Nonnull StageExecution stage) {
        logger.debug("Start of the execute PolicyAgentValidationTask");

        Map<String, Object> exception = new HashMap<String, Object>();

        Map<String, Object> details = new HashMap<String, Object>();

        List errors = new ArrayList();
        errors.add("ERROR: This artifact deployment is blocked by SSD.");

        details.put("error", "Unexpected Task Failure");
        details.put("errors", errors);

        exception.put("details", details);
        exception.put("shouldRetry", false);
        exception.put("operation","SSDPolicyValidation");

        Map<String, Object> taskExecutionDetails = new HashMap<String, Object>();
        taskExecutionDetails.put("exception", exception);

        ///// Case 1: If URL is not correct

        ///// Case 2: If response is not 200
            ///// Case 2.1: if response is 4xx
            ///// Case 2.2: if response is 5xx
        ///// Case 3: If all the required trigger parameters exist
        ///// General denial case

        if (validateDeployment(stage.getExecution().getTrigger().getParameters()))
            return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
        else
            stage.getContext().put("exception", exception);
        logger.debug("End of the execute PolicyAgentValidationTask");
        return TaskResult.builder(ExecutionStatus.TERMINAL).context("taskExceptionDetails", taskExecutionDetails).build();
    }

    private boolean validateDeployment(Map<String, Object> parameters){
        boolean reply = false;
        logger.debug(" Parameters :{}",parameters);
        String requestBody = "{\"account\": \"" + parameters.get("account") + "\", \"appName\": \"" + parameters.get("appName") + "\", \"artifact\": \"" + parameters.get("artifact") + "\", \"clusterName\": \"" + parameters.get("clusterName") + "\", \"service\": \"" + parameters.get("service") + "\", \"teamName\": \"" + parameters.get("teamName") + "\" }";
        logger.debug("!!!!!!!!! Inside policyagentvalidationtask validateDeployment   " + requestBody);

        OkHttpClient ssdClient = new OkHttpClient();
        RequestBody body = RequestBody.create(requestBody,MediaType.get("application/json; charset=utf-8"));
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
            logger.debug("!!!!!!!!! Inside policyagentvalidationtask validateDeployment responseBody " + responseBody);

            // Check if "allow" is true
            if (responseBody.matches(regex)) {
                logger.debug("Access is allowed.");
                reply = true;
            } else {
                logger.debug("Access is denied.");
                reply = false;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        return reply;

    }
}