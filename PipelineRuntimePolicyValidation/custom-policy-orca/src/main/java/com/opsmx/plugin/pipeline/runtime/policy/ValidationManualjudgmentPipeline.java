package com.opsmx.plugin.pipeline.runtime.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.front50.Front50Service;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class ValidationManualjudgmentPipeline {
    private final Logger logger = LoggerFactory.getLogger(ValidationManualjudgmentPipeline.class);
    private static final String RESULT = "result";
    private static final String STATUS = "status";
    private OpaConfigProperties opaConfigProperties;

    private Front50Service front50Service;

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
    public ValidationManualjudgmentPipeline(OpaConfigProperties opaConfigProperties, Front50Service front50Service) {
        logger.debug("Start of the ValidationManualjudgmentPipeline Constructor");
        this.opaConfigProperties = opaConfigProperties;
        this.front50Service = front50Service;
        logger.debug("End of the ValidationManualjudgmentPipeline Constructor");
    }

    public void execute(@NotNull StageExecution stageExecution) {
        logger.debug("Start of the ValidationManualjudgmentPipeline Policy Validation");
        Map<String, Object>  pipelineExecutionMap = pipelineToMapObject(stageExecution.getExecution());
        logger.debug("input :{}",pipelineExecutionMap);
        logger.debug("Manualjudgment  approver validation : {}",(!(opaConfigProperties.isEnabled() && (opaConfigProperties.getManualjudgment() != null && opaConfigProperties.getManualjudgment().isEnabled()))));
        if (!(opaConfigProperties.isEnabled() && (opaConfigProperties.getManualjudgment() != null && opaConfigProperties.getManualjudgment().isEnabled()))) {
            logger.info("Manualjudgment  approver validation not enabled with OPA, returning");
            logger.debug("End of the ValidationManualjudgmentPipeline Policy Validation");
            return;
        }

        try {

            // Form input to opa
            String finalInput = getOpaInput(pipelineExecutionMap);
            logger.debug("Verifying with OPA input :{} ", finalInput);
            /* build our request to OPA */
            RequestBody requestBody = RequestBody.create(JSON, finalInput);
            logger.debug("OPA endpoint : {}", opaConfigProperties.getUrl());
            String opaStringResponse ="{}";

            /* fetch the response from the spawned call execution */
            if (opaConfigProperties.getManualjudgment() != null && !opaConfigProperties.getManualjudgment().getPolicies().isEmpty()) {
                for(OpaConfigProperties.Policy policy: opaConfigProperties.getManualjudgment().getPolicies()){
                    String opaUrl = opaConfigProperties.getRuntimeUrl().endsWith("/") ? opaConfigProperties.getRuntimeUrl().substring(0, opaConfigProperties.getRuntimeUrl().length() - 1) : opaConfigProperties.getRuntimeUrl() + opaConfigProperties.getOpaPolicyLocation();
                    String opaFinalUrl = String.format("%s/%s", opaUrl, policy.getPackageName().startsWith("/") ? policy.getPackageName().substring(1) : policy.getPackageName());
                    logger.debug("opaFinalUrl: {}", opaFinalUrl);
                    Response httpResponse = doPost(opaFinalUrl, requestBody);
                    opaStringResponse = httpResponse.body().string();
                    logger.debug("OPA response: {}", opaStringResponse);
                    logger.debug("statuscode : {}, opaResultKey : {}", httpResponse.code(), opaConfigProperties.getResultKey());
                    if (httpResponse.code() != 200) {
                        stageExecution.getContext().put("judgmentStatus",opaStringResponse);
                        throw new ValidationException(opaStringResponse, null);
                    }else{
                       validateOPAResponse(stageExecution, opaStringResponse);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Communication exception for OPA at {}: {}", opaConfigProperties.getUrl(), e.toString());
            throw new ValidationException(e.toString(), null);
        }
        logger.debug("End of the ValidationManualjudgmentPipeline Policy Validation");
    }

    private Map<String, Object>  getPipeline(StageExecution stageExecution){
            PipelineExecution pipelineExecution = stageExecution.getExecution();

            String applicationName = pipelineExecution.getApplication();
            String pipelineId = pipelineExecution.getPipelineConfigId();
            if (!StringUtils.isEmpty(pipelineId)) {
                return front50Service.getPipelines(applicationName).stream()
                        .filter(m -> m.containsKey("id"))
                        .filter(m -> m.get("id").equals(pipelineId))
                        .findFirst()
                        .orElse(null);
            }
            return null;
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

    private void validateOPAResponse(StageExecution stageExecution, String opaStringResponse){
        JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
        JsonObject opaResult;
        if (opaResponse.has(RESULT)) {
            opaResult = opaResponse.get(RESULT).getAsJsonObject();
            if (opaResult.has(opaConfigProperties.getResultKey())) {
                StringBuilder denyMessage = new StringBuilder();
                extractDenyMessage(opaResponse, denyMessage);
                if (StringUtils.isNotBlank(denyMessage)) {
                    stageExecution.getContext().put("judgmentStatus",denyMessage.toString());
                    throw new ValidationException(denyMessage.toString(), null);
                }
            } else {
                String errorMsg = "There is no '" + opaConfigProperties.getResultKey() + "' field in the OPA response";
                stageExecution.getContext().put("judgmentStatus",errorMsg);
                throw new ValidationException(errorMsg, null);
            }
        } else {
            String errorMsg = "There is no 'result' field in the OPA response";
            stageExecution.getContext().put("judgmentStatus",errorMsg);
            throw new ValidationException(errorMsg, null);
        }
    }

    private void extractDenyMessage(JsonObject opaResponse, StringBuilder messagebuilder) {
        Set<Map.Entry<String, JsonElement>> fields = opaResponse.entrySet();
        fields.forEach(field -> {
            if (field.getKey().equalsIgnoreCase(opaConfigProperties.getResultKey())) {
                JsonArray resultKey = field.getValue().getAsJsonArray();
                if (resultKey.size() != 0) {
                    resultKey.forEach(result -> {
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

    private String getOpaInput(Map<String, Object> pipeline) {
        logger.debug("Start of the getOpaInput");
        String application;
        String pipelineName;
        try {
            //Map newPipeline = pipelineToMapObject(pipeline);
            if (pipeline.containsKey("application")) {
                application = pipeline.get("application").toString();
                pipelineName = pipeline.get("name").toString();
                logger.debug("## application : {}, pipelineName : {}", application, pipelineName);
                logger.debug("End of the getOpaInput");
                return objectMapper.writeValueAsString(addWrapper(addWrapper(pipeline, "pipeline"), "input"));
            } else {
                throw new ValidationException("The received pipeline doesn't have application field", null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Exception occured converting the PipelineExecution :{}", e);
            throw new ValidationException("Failed to convert the PipelineExecution to OPA Input :" + e.toString(), null);
        }
    }

    private Map addWrapper(Map pipeline, String wrapper) {
        Map input = new HashMap();
        input.put(wrapper, pipeline);
        return input;
    }

    private Map pipelineToMapObject(PipelineExecution pipelineExecution) {
        logger.debug("Converting the Pipeline Execution to Map Object");
        return objectMapper.convertValue(pipelineExecution, Map.class);
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
