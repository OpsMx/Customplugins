package com.opsmx.plugin.policy.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class RestartPipelineTask {
    private final Logger logger = LoggerFactory.getLogger(RestartPipelineTask.class);
    private static final String RESULT = "result";
    private static final String STATUS = "status";
    private OpaConfigProperties opaConfigProperties;

    @Autowired
    ObjectMapper objectMapper;

	/* define configurable variables:
	    opaUrl: OPA or OPA-Proxy base url
	    opaResultKey: Not needed for Proxy. The key to watch in the return from OPA.
	    policyLocation: Where in OPA is the policy located, generally this is v0/location/to/policy/path
	                    And for Proxy it is /v1/staticPolicy/eval
	    isOpaEnabled: Policy evaluation is skipped if this is false
	    isOpaProxy : true if Proxy is present instead of OPA server.
	 */


    private final Gson gson = new Gson();

    /* OPA spits JSON */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient opaClient = new OkHttpClient();

    @Autowired
    public RestartPipelineTask(OpaConfigProperties opaConfigProperties) {
        logger.debug("Start of the RestartPipelineTask Constructor");
        this.opaConfigProperties = opaConfigProperties;
        logger.debug("End of the RestartPipelineTask Constructor");
    }

    public void execute(@NotNull StageExecution stageExecution) {
        logger.debug("Start of the RestartPipelineTask Policy Validation");
        if (!opaConfigProperties.isEnabled()) {
            logger.info("OPA not enabled, returning");
            logger.debug("End of the RestartPipelineTask Policy Validation");
            //return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
            return;
        }
        /*if (!stageExecution.isManualJudgmentType()) {
            logger.info("Stage is not Manual Judgment ");
            logger.debug("End of the RestartPipelineTask Policy Validation");
            //return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
            return;
        }*/
        PipelineExecution pipelineExecution = stageExecution.getExecution();
        String finalInput = null;
        Response httpResponse;
        try {
            // Form input to opa
            finalInput = getOpaInput(pipelineExecution);
            logger.debug("Verifying with OPA input :{} ", finalInput);
            /* build our request to OPA */
            RequestBody requestBody = RequestBody.create(JSON, finalInput);
            logger.debug("OPA endpoint : {}", opaConfigProperties.getUrl());
            String opaStringResponse;

            /* fetch the response from the spawned call execution */
            if (!opaConfigProperties.getRuntime().isEmpty()) {
                for(OpaConfigProperties.Policy policy: opaConfigProperties.getRuntime()){
                    String opaFinalUrl = String.format("%s/%s", opaConfigProperties.getUrl().endsWith("/") ? opaConfigProperties.getUrl().substring(0, opaConfigProperties.getUrl().length() - 1) : opaConfigProperties.getUrl(), policy.getPackageName().startsWith("/") ? policy.getPackageName().substring(1) : policy.getPackageName());
                    logger.debug("opaFinalUrl: {}", opaFinalUrl);
                    httpResponse = doPost(opaFinalUrl, requestBody);		;
                    opaStringResponse = httpResponse.body().string();
                    logger.info("OPA response: {}", opaStringResponse);
                    logger.debug("proxy enabled : {}, statuscode : {}, opaResultKey : {}", opaConfigProperties.isProxy(), httpResponse.code(), opaConfigProperties.getResultKey());
                    if (opaConfigProperties.isProxy()) {
                        if (httpResponse.code() != 200) {
                            throw new ValidationException(opaStringResponse, null);
                        }else{
                            validateOPAResponse(opaStringResponse);
                        }
                    } else {
                        validateOPAResponse(opaStringResponse);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Communication exception for OPA at {}: {}", opaConfigProperties.getUrl(), e.toString());
            throw new ValidationException(e.toString(), null);
        }
        logger.debug("End of the RestartPipelineTask Policy Validation");
        //return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
    }

    private boolean isChildPipeline(PipelineExecution pipelineExecution) {
        if (pipelineExecution.getTrigger() != null) {
            Trigger trigger = pipelineExecution.getTrigger();
            if (!StringUtils.isEmpty(trigger.getType()) && trigger.getType().equalsIgnoreCase("pipeline")) {
                return true;
            }
        }
        return false;
    }

    private void validateOPAResponse(String opaStringResponse){
        JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
        JsonObject opaResult;
        if (opaResponse.has(RESULT)) {
            opaResult = opaResponse.get(RESULT).getAsJsonObject();
            if (opaResult.has(opaConfigProperties.getResultKey())) {
                StringBuilder denyMessage = new StringBuilder();
                extractDenyMessage(opaResponse, denyMessage);
                if (StringUtils.isNotBlank(denyMessage)) {
                    throw new ValidationException(denyMessage.toString(), null);
                }
            } else {
                throw new ValidationException("There is no '" + opaConfigProperties.getResultKey() + "' field in the OPA response", null);
            }
        } else {
            throw new ValidationException("There is no 'result' field in the OPA response", null);
        }
    }

private void extractDenyMessage(JsonObject opaResponse, StringBuilder messagebuilder) {
    Set<Map.Entry<String, JsonElement>> fields = opaResponse.entrySet();
    fields.forEach(
            field -> {
                if (field.getKey().equalsIgnoreCase(opaConfigProperties.getResultKey())) {
                    JsonArray resultKey = field.getValue().getAsJsonArray();
                    if (resultKey.size() != 0) {
                        resultKey.forEach(
                                result -> {
                                    if (StringUtils.isNotEmpty(messagebuilder)) {
                                        messagebuilder.append(", ");
                                    }
                                    messagebuilder.append(result.getAsString());
                                });
                    }
                } else if (field.getValue().isJsonObject()) {
                    extractDenyMessage(field.getValue().getAsJsonObject(), messagebuilder);
                } else if (field.getValue().isJsonArray()) {
                    field.getValue().getAsJsonArray().forEach(obj -> {
                        extractDenyMessage(obj.getAsJsonObject(), messagebuilder);
                    });
                }
            });
}

private String getOpaInput(PipelineExecution pipelineExecution) {
    logger.debug("Start of the getOpaInput");
    String application;
    String pipelineName;
    String finalInput = null;
    JsonObject newPipeline = pipelineToJsonObject(pipelineExecution);
    if (newPipeline.has("application")) {
        application = newPipeline.get("application").getAsString();
        pipelineName = newPipeline.get("name").getAsString();
        logger.debug("## application : {}, pipelineName : {}", application, pipelineName);

        finalInput = gson.toJson(addWrapper(addWrapper(newPipeline, "pipeline"), "input"));
    } else {
        throw new ValidationException("The received pipeline doesn't have application field", null);
    }
    logger.debug("End of the getOpaInput");
    return finalInput;
}

private JsonObject addWrapper(JsonObject pipeline, String wrapper) {
    JsonObject input = new JsonObject();
    input.add(wrapper, pipeline);
    return input;
}

private JsonObject pipelineToJsonObject(PipelineExecution pipelineExecution) {
    logger.debug("Start of the pipelineToJsonObject");
    try {
        String pipelineStr = objectMapper.writeValueAsString(pipelineExecution);
        logger.debug("End of the pipelineToJsonObject");
        return gson.fromJson(pipelineStr, JsonObject.class);
    }catch (Exception e){
        e.printStackTrace();
        logger.error("Exception occure while converting the PipelineExecution :{}", e);
        logger.debug("End of the pipelineToJsonObject");
        throw new ValidationException("Conversion Failed while converting the PipelineExecution to Json:" + e.toString(), null);
    }
}

    private Response doPost(String url, RequestBody requestBody) throws IOException {
        Request req = (new Request.Builder()).url(url).post(requestBody).build();
        return getOPAResponse(url, req);
    }
    private Response getOPAResponse(String url, Request req) throws IOException {
        Response httpResponse = this.opaClient.newCall(req).execute();
        ResponseBody responseBody = httpResponse.body();
        if (responseBody == null) {
            throw new IOException("Http call yielded null response!! url:" + url);
        }
        return httpResponse;
    }
}
