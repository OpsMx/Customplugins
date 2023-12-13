package com.opsmx.plugin.stage.custom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import groovy.util.logging.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Extension
@Component
@Slf4j
public class PipelineRbacTask implements Task {

    public static final String STAGE_STATUS = "stageStatus";
    private static final String RESULT = "result";
    private static final String STATUS = "status";
    private static final String POLICY_PATH = "POLICY_PATH";

    @Value("${policy.opa.isd:true}")
    private boolean isISDEnabled;
    @Value("${policy.opa.url:http://oes-server-svc.oes:8085}")
    private String opaUrl;

    @Value("${policy.opa.resultKey:deny}")
    private String opaResultKey;

    @Value("${policy.opa.policyLocation:/v1/staticPolicy/eval}")
    private String opaPolicyLocation;

    @Value("${policy.opa.enabled:false}")
    private boolean isOpaEnabled;

    @Value("${policy.opa.proxy:true}")
    private boolean isOpaProxy;

    @Value("${policy.opa.deltaVerification:false}")
    private boolean deltaVerification;

    @Value("${policy.opa.static.pipeline:}")
    private String staticPolicies;
    private final Gson gson = new Gson();

    @Autowired
    private final ObjectMapper mapper = new ObjectMapper();

    /* OPA spits JSON */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient opaClient = new OkHttpClient();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Optional<FiatService> fiatService;

    private final FiatStatus fiatStatus;

    private final Front50Service front50Service;

    public PipelineRbacTask(Optional<FiatService> fiatService, Front50Service front50Service, FiatStatus fiatStatus) {
        this.fiatService = fiatService;
        this.front50Service = front50Service;
        this.fiatStatus = fiatStatus;
    }

    @NotNull
    @Override
    public TaskResult execute(@NotNull StageExecution stage) {

        if (!isOpaEnabled) {
            logger.info("OPA not enabled, returning");
            return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
        }

        Response httpResponse = null;
        try {
            logger.debug("Stage params: {}", stage.getExecution().getAuthentication().getUser());
            List<String> groupList = new ArrayList<String>();
            if (fiatStatus.isEnabled() && fiatService.isPresent()) {
                UserPermission.View userPermission = fiatService.get().getUserPermission(stage.getExecution().getAuthentication().getUser());
                Set<Role.View> roles = userPermission.getRoles();
                roles.forEach(role -> {
                    groupList.add(role.getName());
                });
            }

            Map<String, Object> pipeline;
            if (!(stage.getContext().get("pipeline") instanceof String)) {
                pipeline = (Map<String, Object>) stage.getContext().get("pipeline");
            } else {
                pipeline = (Map<String, Object>) stage.decodeBase64("/pipeline", Map.class);
            }

            Map<String, Object> existingPipeline = fetchExistingPipeline(pipeline);

            if (existingPipeline != null) {
                ArrayList existingStages = (ArrayList) existingPipeline.get("stages");
                ArrayNode existStageJson = mapper.valueToTree(existingStages);

                ArrayList currentStages = (ArrayList) pipeline.get("stages");
                ArrayNode currentStageJson = mapper.valueToTree(currentStages);

                for (JsonNode newStage : currentStageJson) {
                    Boolean isNew = Boolean.TRUE;
                    for (JsonNode exist : existStageJson) {
                        if (newStage.get("refId").asText().equalsIgnoreCase(exist.get("refId").asText())) {
                            isNew = Boolean.FALSE;
                            if (!exist.equals(newStage)) {
                                ((ObjectNode) newStage).put(STAGE_STATUS, "modified");
                            } else {
                                ((ObjectNode) newStage).put(STAGE_STATUS, "unmodified");
                            }
                        }
                    }
                    if (Boolean.TRUE.equals(isNew)) {
                        ((ObjectNode) newStage).put(STAGE_STATUS, "new");
                    }
                }

                for (JsonNode exist : existStageJson) {
                    Boolean isDeleted = Boolean.TRUE;
                    for (JsonNode newStage : currentStageJson) {
                        if (newStage.get("refId").asText().equalsIgnoreCase(exist.get("refId").asText())) {
                            isDeleted = Boolean.FALSE;
                        }
                    }
                    if (Boolean.TRUE.equals(isDeleted)) {
                        ((ObjectNode) exist).set("requisiteStageRefIds", mapper.createArrayNode());
                        ((ObjectNode) exist).put(STAGE_STATUS, "deleted");
                        currentStageJson.add(exist);
                    }
                }

                pipeline.remove("stages");
                pipeline.put("stages", mapper.convertValue(currentStageJson, ArrayList.class));

            }

            String finalInput = getOpaInput(pipeline, groupList,stage.getExecution().getAuthentication().getUser());

            logger.debug("OPA INPUT REQUESTBODY: {}", finalInput);

            RequestBody requestBody = RequestBody.create(JSON, finalInput);
            String opaFinalUrl = String.format("%s/%s", opaUrl.endsWith("/") ? opaUrl.substring(0, opaUrl.length() - 1) : opaUrl, opaPolicyLocation.startsWith("/") ? opaPolicyLocation.substring(1) : opaPolicyLocation);
            logger.debug("opaFinalUrl: {}", opaFinalUrl);
            String opaStringResponse="{}";
            int statusCode = 200;

            /* fetch the response from the spawned call execution */
            if(isISDEnabled){
                Request req = doPost(opaFinalUrl, requestBody);
                httpResponse = getResponse(opaFinalUrl, req);
                opaStringResponse = httpResponse.body().string();
                statusCode = httpResponse.code();
                validateOPAResponse(opaStringResponse, statusCode);
            }else {
                if (!staticPolicies.isEmpty()) {
                    List<String> policyList = getStaticPolicies();
                    for(String policy: policyList){
                        opaFinalUrl = opaFinalUrl.replace(POLICY_PATH, policy); Request req = doPost(opaFinalUrl, requestBody);
                        logger.debug("opaFinalUrl: {}", opaFinalUrl);
                        Map<String, Object> responseObject = getOPAResponse(opaFinalUrl, req);
                        opaStringResponse = String.valueOf(responseObject.get(RESULT));
                        statusCode = Integer.valueOf(responseObject.get(STATUS).toString());
                        validateOPAResponse(opaStringResponse, statusCode);
                    }
                }
            }


        } catch (IOException e) {
            logger.error("Communication exception for OPA at {}: {}", this.opaUrl, e.toString());
            throw new IllegalArgumentException(e.getMessage(), null);
        }

		return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
    }

    private void validateOPAResponse(String opaStringResponse, int statusCode) {
        logger.debug("OPA response: {}", opaStringResponse);
        logger.debug("proxy enabled : {}, statuscode : {}, opaResultKey : {}", isOpaProxy, statusCode, opaResultKey);
        if (isOpaProxy) {
            if (statusCode == 401) {
                JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
                StringBuilder denyMessage = new StringBuilder();
                extractDenyMessage(opaResponse, denyMessage);
                if (StringUtils.isNotBlank(denyMessage)) {
                    throw new IllegalArgumentException("OpsMx Policy Error(s) - " + denyMessage.toString());
                } else {
                    throw new IllegalArgumentException("There is no '" + opaResultKey + "' field in the OPA response", null);
                }
            } else if (statusCode != 200) {
                throw new IllegalArgumentException(opaStringResponse, null);
            }
        } else {
            if (statusCode == 401) {
                JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
                StringBuilder denyMessage = new StringBuilder();
                extractDenyMessage(opaResponse, denyMessage);
                if (StringUtils.isNotBlank(denyMessage)) {
                    throw new IllegalArgumentException("OpsMx Policy Error(s): " + denyMessage.toString());
                } else {
                    throw new IllegalArgumentException("There is no '" + opaResultKey + "' field in the OPA response", null);
                }
            } else if (statusCode != 200) {
                throw new IllegalArgumentException(opaStringResponse, null);
            }
        }
    }

    private List<String> getStaticPolicies() {
        if(staticPolicies.contains(",")) {
            return Arrays.asList(staticPolicies.split(",", -1));
        }else{
            List<String> policies = new ArrayList<>();
            if(!staticPolicies.isEmpty()){
                policies.add(staticPolicies);
            }
            return policies;
        }
    }

    private Map<String, Object> fetchExistingPipeline(Map<String, Object> newPipeline) {
        String applicationName = (String) newPipeline.get("application");
        String newPipelineID = (String) newPipeline.get("id");
        if (!StringUtils.isEmpty(newPipelineID)) {
            return front50Service.getPipelines(applicationName).stream()
                    .filter(m -> m.containsKey("id"))
                    .filter(m -> m.get("id").equals(newPipelineID))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void extractDenyMessage(JsonObject opaResponse, StringBuilder messageBuilder) {
        Set<Map.Entry<String, JsonElement>> fields = opaResponse.entrySet();
        fields.forEach(field -> {
            if (field.getKey().equalsIgnoreCase(opaResultKey)) {
                JsonArray resultKey = field.getValue().getAsJsonArray();
                if (resultKey.size() != 0) {
                    resultKey.forEach(result -> {
                        if (StringUtils.isNotEmpty(messageBuilder)) {
                            messageBuilder.append(", ");
                        }
                        messageBuilder.append(result.getAsString());
                    });
                }
            }else if (field.getValue().isJsonObject()) {
                if (!field.getValue().isJsonPrimitive()) {
                    extractDenyMessage(field.getValue().getAsJsonObject(), messageBuilder);
                }
            } else if (field.getValue().isJsonArray()){
                field.getValue().getAsJsonArray().forEach(obj -> {
                    if (!obj.isJsonPrimitive()) {
                        extractDenyMessage(obj.getAsJsonObject(), messageBuilder);
                    }
                });
            }
        });
    }

    private String getOpaInput(Map<String, Object> pipeline, List<String> roles, String user) {
        String application;
        String pipelineName;
        String finalInput = null;
        boolean initialSave = false;
        JsonObject newPipeline = pipelineToJsonObject(pipeline, roles, user);
        if (newPipeline.has("application")) {
            application = newPipeline.get("application").getAsString();
            pipelineName = newPipeline.get("name").getAsString();
            logger.debug("## application : {}, pipelineName : {}", application, pipelineName);
            // if deltaVerification is true, add both current and new pipelines in single json

            finalInput = gson.toJson(addWrapper(addWrapper(newPipeline, "pipeline"), "input"));
        } else {
            throw new ValidationException("The received pipeline doesn't have application field", null);
        }
        return finalInput;
    }

    private JsonObject addWrapper(JsonObject pipeline, String wrapper) {
        JsonObject input = new JsonObject();
        input.add(wrapper, pipeline);
        return input;
    }

    private JsonObject pipelineToJsonObject(Map<String, Object> pipeline, List<String> roles, String userName) {
        Map<String, Object> userObject = new HashMap<>();
        userObject.put("name", userName);
        userObject.put("groups", roles);

        pipeline.put("user", userObject);
        String pipelineStr = gson.toJson(pipeline);
        return gson.fromJson(pipelineStr, JsonObject.class);
    }

    /*
     * private Response doGet(String url) throws IOException { Request req = (new
     * Request.Builder()).url(url).get().build(); return getResponse(url, req); }
     */

    private Request doPost(String url, RequestBody requestBody) throws IOException {
        return (new Request.Builder()).url(url).post(requestBody).build();
    }
    private Response getResponse(String url, Request req) throws IOException {
        Response httpResponse = this.opaClient.newCall(req).execute();
        ResponseBody responseBody = httpResponse.body();
        if (responseBody == null) {
            throw new IOException("Http call yielded null response!! url:" + url);
        }
        return httpResponse;
    }
    private  Map<String, Object> getOPAResponse(String url, Request req) throws IOException {
        Map<String, Object> apiResponse = new HashMap<>();
        Response httpResponse = this.opaClient.newCall(req).execute();
        String response = httpResponse.body().string();
        if (response == null) {
            throw new IOException("Http call yielded null response!! url:" + url);
        }
        apiResponse.put(RESULT, response);
        logger.debug("## OPA Server response: {}", response );
        JsonObject responseJson = gson.fromJson(response, JsonObject.class);
        if(!responseJson.has(RESULT)){
            //No "result" field? It could be due to incorrect policy path
            logger.error("No 'result' field in the response - {}. OPA api - {}" ,response, req);
            apiResponse.put(STATUS, HttpStatus.BAD_REQUEST.value());
            return apiResponse;
        }
        JsonObject resultJson = responseJson.get(RESULT).getAsJsonObject();
        apiResponse.put(RESULT, gson.toJson(resultJson));
        logger.debug("## resultJson : {}", resultJson);
        if(!resultJson.has("deny")) {
            //No "deny" field? that's weird
            logger.error("No 'deny' field in the response - {}. OPA api - {}",response, req);
            apiResponse.put(STATUS, HttpStatus.BAD_REQUEST.value());
            return apiResponse;
        }
        if(resultJson.get("deny").getAsJsonArray().size() > 0) {
            apiResponse.put(STATUS, HttpStatus.UNAUTHORIZED.value());
        }else{
            //Number of denies are zero
            apiResponse.put(STATUS, HttpStatus.OK.value());
        }
        return apiResponse;
    }
}