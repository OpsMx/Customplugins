package com.opsmx.plugin.stage.custom;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.opsmx.plugin.stage.custom.model.ApplicationModel;
import com.opsmx.plugin.stage.custom.model.GateModel;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netflix.spinnaker.kork.plugins.api.PluginComponent;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Extension
@PluginComponent
public class VerificationTriggerTask implements Task {

	@Value("${isd.gate.url:http://oes-gate:8084}")
	private String isdGateUrl;

	private static final String METRIC = "metric";

	private static final String LOG = "log";

	private static final String SERVICE_GATE = "serviceGate";

	private static final String PIPELINE_NAME = "pipelineName";

	private static final String MINIMUM_CANARY_RESULT_SCORE = "minimumCanaryResultScore";

	private static final String CANARY_CONFIG = "canaryConfig";

	private static final String CANARY_ID = "canaryId";
	
	private static final String PAYLOAD_CONSTRAINT = "payloadConstraint";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final Gson gson = new Gson();

	@NotNull
	@Override
	public TaskResult execute(@NotNull StageExecution stage) {
		logger.info("Application name : {}, Service/pipeline name : {}, Stage name : {}",
				stage.getExecution().getApplication(), stage.getExecution().getName(), stage.getName());
		try {
			ApplicationModel applicationModel = correctTheAppDetails(stage);
			if (applicationModel!=null && !applicationModel.getCustomGateFound()){
				//create verification gate
				createVerificationGate(stage, applicationModel);
			}
			return triggerAnalysis(stage);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	private TaskResult triggerAnalysis(StageExecution stage) throws UnsupportedEncodingException {
		Map<String, Object> contextMap = new HashMap<>();
		Map<String, Object> outputs = new HashMap<>();
		long startTime = Instant.now().toEpochMilli();
		String triggerUrl = getTriggerURL(stage, outputs);
		if (triggerUrl == null) {
			return TaskResult.builder(ExecutionStatus.TERMINAL)
					.context(contextMap)
					.outputs(outputs)
					.build();
		}

		return triggerAnalysis(stage, outputs, contextMap, triggerUrl, startTime);
	}

	private TaskResult triggerAnalysis(StageExecution stage, Map<String, Object> outputs,Map<String, Object> contextMap, String triggerUrl, Long startTime) {
		CloseableHttpClient httpClient = HttpClients.createDefault();
		try {
			String triggerPayload = getPayloadString(stage, startTime);
			outputs.put("trigger_json", String.format("Payload Json - %s", triggerPayload));
			HttpPost requestPost = new HttpPost(triggerUrl);
			requestPost.setEntity(new StringEntity(triggerPayload));
			requestPost.setHeader("Content-type", "application/json");
			requestPost.setHeader("x-spinnaker-user", stage.getExecution().getAuthentication().getUser());

			CloseableHttpResponse response = httpClient.execute(requestPost);
			HttpEntity entity = response.getEntity();
			String registerResponse = "";
			if (entity != null) {
				registerResponse = EntityUtils.toString(entity);
			}

			logger.info("Verification trigger response : {}, User : {}", registerResponse, stage.getExecution().getAuthentication().getUser());
			if (response.getStatusLine().getStatusCode() != 202) {
				outputs.put(OesConstants.EXCEPTION, String.format("Failed to trigger request with Status code : %s and Response : %s",
						response.getStatusLine().getStatusCode(), registerResponse));
				outputs.put(OesConstants.OVERALL_SCORE, 0.0);
				outputs.put(OesConstants.OVERALL_RESULT, "Fail");
				outputs.put(OesConstants.TRIGGER, OesConstants.FAILED);
				return TaskResult.builder(ExecutionStatus.TERMINAL)
						.context(contextMap)
						.outputs(outputs)
						.build();
			}

			ObjectNode readValue = objectMapper.readValue(registerResponse, ObjectNode.class);
			String canaryId = readValue.get(CANARY_ID).asText();
			if (canaryId == null || canaryId.isEmpty()) {
				outputs.put(OesConstants.EXCEPTION, "Something went wrong while triggering registry analysis");
				outputs.put(OesConstants.OVERALL_SCORE, 0.0);
				outputs.put(OesConstants.OVERALL_RESULT, "Fail");
				outputs.put(OesConstants.TRIGGER, OesConstants.FAILED);
				return TaskResult.builder(ExecutionStatus.TERMINAL)
						.context(contextMap)
						.outputs(outputs)
						.build();
			}

			String canaryUrl = response.getLastHeader(OesConstants.LOCATION).getValue();
			logger.debug("Analysis autopilot link : {}", canaryUrl);
			outputs.put(OesConstants.LOCATION, canaryUrl);
			outputs.put(OesConstants.TRIGGER, OesConstants.SUCCESS);
			return TaskResult.builder(ExecutionStatus.SUCCEEDED)
					.context(contextMap)
					.outputs(outputs)
					.build();
		} catch (Exception e) {
			logger.error("Failed to execute verification gate", e);
			outputs.put(OesConstants.EXCEPTION, String.format("Error occurred while triggering analysis, %s", e));
			outputs.put(OesConstants.OVERALL_SCORE, 0.0);
			outputs.put(OesConstants.OVERALL_RESULT, "Fail");
			outputs.put(OesConstants.TRIGGER, OesConstants.FAILED);
			return TaskResult.builder(ExecutionStatus.TERMINAL)
					.context(contextMap)
					.outputs(outputs)
					.build();
		} finally {
			if (httpClient != null) {
				try {
					httpClient.close();
				} catch (IOException e) {
					logger.warn("exception while closing the connection : {}",
							e.getMessage());
				}
			}
		}
	}

	private String getTriggerURL(StageExecution stage, Map<String, Object> outputs) throws UnsupportedEncodingException {

		String triggerEndpoint = constructGateEnpoint(stage);
		CloseableHttpClient httpClient = HttpClients.createDefault();
		try {
			HttpGet request = new HttpGet(triggerEndpoint);
			request.setHeader("Content-type", "application/json");
			request.setHeader("x-spinnaker-user", stage.getExecution().getAuthentication().getUser());
			CloseableHttpResponse response = httpClient.execute(request);

			HttpEntity entity = response.getEntity();
			String registerResponse = "";
			if (entity != null) {
				registerResponse = EntityUtils.toString(entity);
			}

			logger.debug("STATUS CODE: {}, RESPONSE : {}", response.getStatusLine().getStatusCode(), registerResponse);
			if (response.getStatusLine().getStatusCode() != 200) {
				outputs.put(OesConstants.EXCEPTION, String.format("Failed to get trigger endpoint with response : %s", registerResponse));
				outputs.put(OesConstants.OVERALL_SCORE, 0.0);
				outputs.put(OesConstants.OVERALL_RESULT, "Fail");
				outputs.put(OesConstants.TRIGGER, OesConstants.FAILED);
				return null;
			}

			ObjectNode readValue = objectMapper.readValue(registerResponse, ObjectNode.class);
			String triggerUrl = readValue.get("gateUrl").isNull() ? null : readValue.get("gateUrl").asText();
			if (triggerUrl == null || triggerUrl.isEmpty() || triggerUrl.equalsIgnoreCase("null")) {
				outputs.put("Reason", String.format("Failed to get trigger endpoint with response :: %s", registerResponse));
				outputs.put(OesConstants.EXCEPTION, "Failed to get trigger endpoint. Please resave the stage before execution");
				outputs.put(OesConstants.OVERALL_SCORE, 0.0);
				outputs.put(OesConstants.OVERALL_RESULT, "Fail");
				outputs.put(OesConstants.TRIGGER, OesConstants.FAILED);
				return null;
			}
			return triggerUrl;
		} catch (Exception e) {
			logger.error("Failed to execute verification gate", e);
			outputs.put(OesConstants.EXCEPTION, String.format("Error occurred while getting trigger endpoint, %s", e));
			outputs.put(OesConstants.OVERALL_SCORE, 0.0);
			outputs.put(OesConstants.OVERALL_RESULT, "Fail");
			outputs.put(OesConstants.TRIGGER, OesConstants.FAILED);
			return null;
		} finally {
			if (httpClient != null) {
				try {
					httpClient.close();
				} catch (IOException e) {
					logger.warn("exception while closing the connection : {}",
							e.getMessage());
				}
			}
		}
	}

	private String constructGateEnpoint(StageExecution stage) throws UnsupportedEncodingException {
		//applications/{applicationname}/pipeline/{pipelineName}/reference/{ref}/gates/{gatesName}?type={gateType}
		return String.format("%s/platformservice/v6/applications/%s/pipeline/%s/reference/%s/gates/%s?type=verification",
				isdGateUrl.endsWith("/") ? isdGateUrl.substring(0, isdGateUrl.length() - 1) : isdGateUrl,
				stage.getExecution().getApplication(), encodeString(stage.getExecution().getName()), stage.getRefId(),
				encodeString(stage.getName()));
	}

	private String encodeString(String value) throws UnsupportedEncodingException {
		return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
	}

	private String getPayloadString(StageExecution stage, Long startTime) throws JsonProcessingException {

		ObjectNode finalJson = objectMapper.createObjectNode();
		finalJson.put("application", stage.getExecution().getApplication());
		finalJson.put("isJsonResponse", true);
		finalJson.put("executionId", stage.getExecution().getId());
		Map<String, Object> parameterContext = (Map<String, Object>) stage.getContext().get("parameters");
		ArrayNode payloadConstraintNode = objectMapper.createArrayNode();
		if (parameterContext.get("gateSecurity") != null) {
			String gateSecurityPayload = objectMapper.writeValueAsString(parameterContext.get("gateSecurity"));
			ArrayNode securityNode = (ArrayNode) objectMapper.readTree(gateSecurityPayload);
			securityNode.forEach(secNode -> {
				ArrayNode valuesNode = (ArrayNode)secNode.get("values");
				for (JsonNode jsonNode : valuesNode) {
					if (!jsonNode.get("label").isNull() && !jsonNode.get("label").asText().isEmpty()) {
						payloadConstraintNode.add(objectMapper.createObjectNode()
								.put(jsonNode.get("label").asText(), jsonNode.get("value").isNull() ? null : jsonNode.get("value").asText()));
					}
				}
			});
		}
		finalJson.set(PAYLOAD_CONSTRAINT, payloadConstraintNode);

		ObjectNode canaryConfig = objectMapper.createObjectNode();
		canaryConfig.put("lifetimeMinutes", parameterContext.get("lifetime") != null ? (String) parameterContext.get("lifetime") : "6");
		canaryConfig.set("canaryHealthCheckHandler", objectMapper.createObjectNode()
				.put(MINIMUM_CANARY_RESULT_SCORE, parameterContext.get("minicanaryresult") != null ? (String) parameterContext.get("minicanaryresult") : "70" ));
		canaryConfig.set("canarySuccessCriteria", objectMapper.createObjectNode()
				.put("canaryResultScore", parameterContext.get("canaryresultscore") != null ? (String) parameterContext.get("canaryresultscore") : "90"));
		canaryConfig.put("name", stage.getExecution().getAuthentication().getUser());

		ObjectNode baselinePayload = objectMapper.createObjectNode();
		ObjectNode canaryPayload = objectMapper.createObjectNode();
		if (parameterContext.get("logTemplate") != null && ! ((String) parameterContext.get("logTemplate")).isEmpty()) {
			baselinePayload.set(LOG,
					prepareJson(stage.getName(), stage.getExecution().getName()));
			canaryPayload.set(LOG,
					prepareJson(stage.getName(), stage.getExecution().getName()));
		}
		if (parameterContext.get("metricTemplate") != null && !((String) parameterContext.get("metricTemplate")).isEmpty()) {
			baselinePayload.set(METRIC,
					prepareJson(stage.getName(), stage.getExecution().getName()));
			canaryPayload.set(METRIC,
					prepareJson(stage.getName(), stage.getExecution().getName()));
		}

		ObjectNode triggerPayload = objectMapper.createObjectNode();
		triggerPayload.set("baseline", baselinePayload);
		triggerPayload.set("canary", canaryPayload);

		ArrayNode payloadTriggerNode = objectMapper.createArrayNode();
		payloadTriggerNode.add(triggerPayload);
		if (parameterContext.get("baselineRealTime") != null && parameterContext.get("baselineRealTime").equals(Boolean.TRUE)) {
			triggerPayload.put("baselineStartTimeMs", startTime);
		} else {
			Long baselinestarttime = null;
			try{
				baselinestarttime = parameterContext.get("baselinestarttime") != null ? (Long) parameterContext.get("baselinestarttime") : startTime;
			} catch(ClassCastException cce){
				baselinestarttime = parameterContext.get("baselinestarttime") != null ? Double.valueOf( (Double) parameterContext.get("baselinestarttime")).longValue() : startTime;
			}
			triggerPayload.put("baselineStartTimeMs",
					baselinestarttime);
		}

		if (parameterContext.get("canaryRealTime") != null && parameterContext.get("canaryRealTime").equals(Boolean.TRUE)) {
			triggerPayload.put("canaryStartTimeMs", startTime);
		} else {
			Long canarystarttime = null;
			try {
				canarystarttime = parameterContext.get("canarystarttime") != null ? (Long) parameterContext.get("canarystarttime") : startTime;
			} catch (ClassCastException cce){
				canarystarttime = parameterContext.get("canarystarttime") != null ? Double.valueOf((Double) parameterContext.get("canarystarttime")).longValue() : startTime;
			}
			triggerPayload.put("canaryStartTimeMs",
					canarystarttime);
		}

		finalJson.set(CANARY_CONFIG, canaryConfig);
		finalJson.set("canaryDeployments", payloadTriggerNode);
		return objectMapper.writeValueAsString(finalJson);
	}

	private JsonNode prepareJson(String stageName, String pipelineName) {
		return objectMapper.createObjectNode().set(pipelineName,
				objectMapper.createObjectNode()
						.put(PIPELINE_NAME, pipelineName)
						.put(SERVICE_GATE, stageName));
	}

	private ApplicationModel correctTheAppDetails(@NotNull StageExecution stage) throws IOException {
		Map<String, Object> context = stage.getContext();
		ApplicationModel applicationModel = null;

		if (context.containsKey("applicationId") && context.containsKey("serviceId") && context.containsKey("pipelineId")){

			logger.debug("State of the context before modification : {}", context);
			applicationModel = getAppDetails(stage);
			context.put("applicationId", applicationModel.getAppId());
			context.put("serviceId", applicationModel.getServiceId());
			context.put("pipelineId", applicationModel.getPipelineId());
			stage.setContext(context);
			logger.debug("context modified : {}", stage.getContext());
		}
		return applicationModel;
	}

	private ApplicationModel getAppDetails(StageExecution stage) throws IOException {

		try (CloseableHttpClient httpClient = HttpClients.createDefault()){
			String appDetailsUrl = getAppDetailsUrl(stage);
			logger.debug("Invoking the URL : {} to fetch the app details", appDetailsUrl);
			HttpGet request = new HttpGet(appDetailsUrl);
			request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			request.setHeader(OesConstants.X_SPINNAKER_USER, stage.getExecution().getAuthentication().getUser());

			ApplicationModel applicationModel = gson.fromJson(EntityUtils.toString(httpClient.execute(request).getEntity()), ApplicationModel.class);
			logger.debug("Application details response : {}", applicationModel);
			return applicationModel;
		}
	}

	@NotNull
	private String getAppDetailsUrl(StageExecution stage) {
		return getIsdGateUrl()
				+ OesConstants.GET_APPDETAILS_URL.replace("{applicationName}", stage.getExecution().getApplication()).replace("{pipelineName}", stage.getExecution().getName()) + "&refId=" + stage.getRefId() + "&gateName=" + stage.getName() + "&gateType=" + stage.getType();
	}

	private String getIsdGateUrl(){
		return isdGateUrl.endsWith("/") ? isdGateUrl.substring(0, isdGateUrl.length() - 1) : isdGateUrl;
	}

	private void createVerificationGate(StageExecution stage, ApplicationModel applicationModel) throws Exception{

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			String createGateUrl = getCreateGateUrl(applicationModel);
			logger.debug("Create Verification GATE url : {}", createGateUrl);
			HttpPost request = new HttpPost(createGateUrl);

			GateModel gateModel = new GateModel();

			String stringParam = gson.toJson(stage.getContext().get("parameters"), Map.class);
			logger.debug("Verification GATE parameters : {}", stringParam);

			JsonObject parameters = gson.fromJson(stringParam, JsonObject.class);

			gateModel.setApplicationId(applicationModel.getAppId().toString());
			gateModel.setGateName(stage.getName());
			gateModel.setDependsOn(new ArrayList<>(stage.getRequisiteStageRefIds()));
			gateModel.setGateType(stage.getType());
			gateModel.setRefId(stage.getRefId());
			gateModel.setPipelineId(applicationModel.getPipelineId());

			//Verification Gate specific details start
			if (parameters.has("logTemplate")) {
				gateModel.setLogTemplateName(parameters.get("logTemplate").getAsString().trim());
			}else {
				throw new IllegalArgumentException("Verification gate requires a Log Template to be configured");
			}

			if (parameters.has("metricTemplate")) {
				gateModel.setMetricTemplateName(parameters.get("metricTemplate").getAsString().trim());
			}else {
				throw new IllegalArgumentException("Verification gate requires a Metric Template to be configured");
			}
			//Verification Gate specific details end

			gateModel.setEnvironmentId(getEnvironmentId(parameters));
			gateModel.setPayloadConstraint(getPayloadConstraints(parameters));

			request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			request.setHeader(OesConstants.X_SPINNAKER_USER, stage.getExecution().getAuthentication().getUser());
			request.setHeader(HttpHeaders.ORIGIN, OesConstants.PLUGIN_NAME);
			String body = objectMapper.writeValueAsString(gateModel);
			request.setEntity(new StringEntity(body));

			logger.debug("Create Verification GATE request body : {}", body);

			CloseableHttpResponse response = httpClient.execute(request);
			if (response.getStatusLine().getStatusCode() == HttpStatus.OK.value()){
				logger.info("Successfully created Verification GATE");
				logger.debug("Create Verification GATE response body : {}", EntityUtils.toString(response.getEntity()));
			}
		}
	}

	@NotNull
	private String getCreateGateUrl(ApplicationModel applicationModel) {
		return getIsdGateUrl() + OesConstants.CREATE_GATE_URL.replace("{pipelineId}", applicationModel.getPipelineId().toString());
	}

	private Integer getEnvironmentId(JsonObject parameters){
		JsonObject environmentJsonObject = parameters.getAsJsonArray("environment").get(0).getAsJsonObject();
		return environmentJsonObject.get("id").getAsInt();
	}

	private List<Map<String, String>> getPayloadConstraints(JsonObject parameters) {
		// Adding payload constraint to stage / gate creation request
		List<Map<String, String>> payloadConstraints = new ArrayList<>();
		JsonObject payloadJsonObject;

		if (parameters.has("gateSecurity")) {
			payloadJsonObject = parameters.getAsJsonArray("gateSecurity").get(0).getAsJsonObject();

			if (payloadJsonObject.has("values")) {
				JsonArray valuesArray = payloadJsonObject.getAsJsonArray("values");

				if (valuesArray != null && valuesArray.size() > 0) {
					for (JsonElement value : valuesArray) {
						if (value.getAsJsonObject().has("label") &&
								!value.getAsJsonObject().get("label").getAsString().isEmpty()) {
							Map<String, String> valuesMap = new HashMap<>();
							valuesMap.put(value.getAsJsonObject().get("label").getAsString(), value.getAsJsonObject().get("value").getAsString());
							payloadConstraints.add(valuesMap);
						}
					}
				}
			}
		}
		return payloadConstraints;
	}
}
