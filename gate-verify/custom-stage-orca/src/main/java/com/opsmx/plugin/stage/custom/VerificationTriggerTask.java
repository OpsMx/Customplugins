package com.opsmx.plugin.stage.custom;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
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

	@NotNull
	@Override
	public TaskResult execute(@NotNull StageExecution stage) {
		logger.info("Application name : {}, Service/pipeline name : {}, Stage name : {}",
				stage.getExecution().getApplication(), stage.getExecution().getName(), stage.getName());
		return triggerAnalysis(stage);
	}


	private TaskResult triggerAnalysis(StageExecution stage) {
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

	private String getTriggerURL(StageExecution stage, Map<String, Object> outputs) {

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
			if (triggerUrl == null || triggerUrl.isBlank() || triggerUrl.equalsIgnoreCase("null")) {
				outputs.put(OesConstants.EXCEPTION, String.format("Failed to get trigger endpoint with response : %s", registerResponse));
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

	private String constructGateEnpoint(StageExecution stage) {
		//applications/{applicationname}/pipeline/{pipelineName}/reference/{ref}/gates/{gatesName}?type={gateType}
		return String.format("%s/platformservice/v6/applications/%s/pipeline/%s/reference/%s/gates/%s?type=verification",
				isdGateUrl.endsWith("/") ? isdGateUrl.substring(0, isdGateUrl.length() - 1) : isdGateUrl,
				stage.getExecution().getApplication(), stage.getExecution().getName(), stage.getRefId(),
				stage.getName());
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
					if (!jsonNode.get("label").isNull() && !jsonNode.get("label").asText().isBlank()) {
						payloadConstraintNode.add(objectMapper.createObjectNode()
								.put(jsonNode.get("label").asText(), jsonNode.get("value").isNull() ? null : jsonNode.get("value").asText()));
					}
				}
			});
		}
		finalJson.set(PAYLOAD_CONSTRAINT, payloadConstraintNode);

		ObjectNode canaryConfig = objectMapper.createObjectNode();
		canaryConfig.put("lifetimeHours", parameterContext.get("lifetime") != null ? (String) parameterContext.get("lifetime") : "0.152");
		canaryConfig.set("canaryHealthCheckHandler", objectMapper.createObjectNode()
				.put(MINIMUM_CANARY_RESULT_SCORE, parameterContext.get("minicanaryresult") != null ? (String) parameterContext.get("minicanaryresult") : "70" ));
		canaryConfig.set("canarySuccessCriteria", objectMapper.createObjectNode()
				.put("canaryResultScore", parameterContext.get("canaryresultscore") != null ? (String) parameterContext.get("canaryresultscore") : "90"));
		canaryConfig.put("name", stage.getExecution().getAuthentication().getUser());

		ObjectNode baselinePayload = objectMapper.createObjectNode();
		ObjectNode canaryPayload = objectMapper.createObjectNode();
		if (parameterContext.get("logTemplate") != null && ! ((String) parameterContext.get("logTemplate")).isBlank()) {
			baselinePayload.set(LOG,
					prepareJson(stage.getName(), stage.getExecution().getName()));
			canaryPayload.set(LOG,
					prepareJson(stage.getName(), stage.getExecution().getName()));
		}
		if (parameterContext.get("metricTemplate") != null && !((String) parameterContext.get("metricTemplate")).isBlank()) {
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
			triggerPayload.put("baselineStartTimeMs", startTime);
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
}
