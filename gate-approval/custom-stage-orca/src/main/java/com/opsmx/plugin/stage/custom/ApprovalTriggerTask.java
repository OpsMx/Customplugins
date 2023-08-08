package com.opsmx.plugin.stage.custom;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.opsmx.plugin.stage.custom.constants.Constants;
import com.opsmx.plugin.stage.custom.model.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
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
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.ResponseEntity;

@Extension
@PluginComponent
public class ApprovalTriggerTask implements Task {

	@Value("${isd.gate.url:http://oes-gate:8084}")
	private String isdGateUrl;

	private static final String PAYLOAD_CONSTRAINT = "payloadConstraint";

	private static final String REPOSITORY_PATH = "repositoryPath";

	private static final String ARTIFACTORY = "ARTIFACTORY";

	private static final String TYPE = "type";

	private static final String NUMBER = "number";

	private static final String SERVICENOW = "SERVICENOW";

	private static final String REPOSITORY_NAME = "repositoryName";

	private static final String BITBUCKET = "BITBUCKET";

	private static final String IMAGE_ID = "imageId";

	private static final String PRISMACLOUD = "PRISMACLOUD";

	public static final String FAILED = "FAILED";

	public static final String SUCCESS = "SUCCESS";

	public static final String TRIGGER = "trigger";

	private static final String TRIGGER_JSON = "trigger_json";

	private static final String CONNECTORS = "connectors";

	private static final String VALUES = "values";

	private static final String BUILD_NUMBER = "buildNumber";

	private static final String PLAN_NAME = "planName";

	private static final String PROJECT_NAME = "projectName";

	private static final String WATCH_NAME = "watch_name";

	private static final String JFROG = "JFROG";

	private static final String BAMBOO = "BAMBOO";

	private static final String REPORT_ID = "reportId";

	private static final String REJECTED = "rejected";

	private static final String LOCATION = "location";

	private static final String TOOL_CONNECTOR_PARAMETERS = "toolConnectorParameters";

	private static final String CANARY_ID = "canaryId";

	private static final String AUTOPILOT = "AUTOPILOT";

	private static final String AQUAWAVE = "AQUAWAVE";

	private static final String APPSCAN = "APPSCAN";

	private static final String PROJECT_KEY = "projectKey";

	private static final String SONARQUBE = "SONARQUBE";

	private static final String ARTIFACT = "artifact";

	private static final String BUILD_ID = "buildId";

	private static final String JOB = "job";

	private static final String JENKINS = "JENKINS";

	private static final String REPO = "repo";

	private static final String COMMIT_ID = "commitId";

	private static final String GIT = "GIT";

	private static final String JIRA_TICKET_NO = "jira_ticket_no";

	private static final String PARAMETERS = "parameters";

	private static final String CONNECTOR_TYPE = "connectorType";

	private static final String JIRA = "JIRA";

	private static final String EXCEPTION = "exception";

	public static final String STATUS = "status";

	public static final String NAVIGATIONAL_URL = "navigationalURL";

	public static final String APPROVAL_URL = "approvalUrl";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Gson gson = new Gson();



	@Autowired
	private final ObjectMapper objectMapper = new ObjectMapper();

	@NotNull
	@Override
	public TaskResult execute(@NotNull StageExecution stage) {

		Map<String, Object> contextMap = new HashMap<>();
		Map<String, Object> outputs = new HashMap<>();

		logger.info("Approval execution started, Application name : {}, Pipeline name : {}", stage.getExecution().getApplication(), stage.getExecution().getName());
		CloseableHttpClient httpClient = null;
		try {

			ApplicationModel applicationModel = correctTheAppDetails(stage);
			if (applicationModel!=null && !applicationModel.getCustomGateFound()){
				createApprovalGate(stage, applicationModel);
			}
			String triggerUrl = getTriggerURL(stage, outputs);
			if (triggerUrl == null) {
				return TaskResult.builder(ExecutionStatus.TERMINAL)
						.context(contextMap)
						.outputs(outputs)
						.build();
			}


			logger.info("Application name : {}, pipeline name : {}", stage.getExecution().getApplication(), stage.getExecution().getName());
			HttpPost request = new HttpPost(triggerUrl);
			String triggerPayload = preparePayload((Map<String, Object>) stage.getContext().get("parameters"), stage.getExecution().getId());
			outputs.put(TRIGGER_JSON, String.format("Payload json - %s", triggerPayload));
			request.setEntity(new StringEntity(triggerPayload));
			request.setHeader("Content-type", "application/json");
			request.setHeader("x-spinnaker-user", stage.getExecution().getAuthentication().getUser());

			httpClient = HttpClients.createDefault();
			CloseableHttpResponse response = httpClient.execute(request);

			HttpEntity entity = response.getEntity();
			String registerResponse = "";
			if (entity != null) {
				registerResponse = EntityUtils.toString(entity);
			}
			logger.info("Approval trigger response : {}", registerResponse);
			if (response.getStatusLine().getStatusCode() != 202) {
				logger.info("Failed to trigger approval request with Status code : {}, Response : {}", response.getStatusLine().getStatusCode(), registerResponse);
				outputs.put(EXCEPTION, String.format("Failed to trigger approval request with Status code : %s and Response : %s", response.getStatusLine().getStatusCode(), registerResponse));
				outputs.put(TRIGGER, FAILED);
				outputs.put(STATUS, REJECTED);
				return TaskResult.builder(ExecutionStatus.TERMINAL)
						.context(contextMap)
						.outputs(outputs)
						.build();
			}

			ObjectNode readValue = objectMapper.readValue(registerResponse, ObjectNode.class);
			outputs.put(LOCATION, response.getLastHeader(LOCATION).getValue());
			outputs.put(TRIGGER, SUCCESS);
			if (readValue.get(NAVIGATIONAL_URL) != null  &&  !readValue.get(NAVIGATIONAL_URL).isNull()) {
				outputs.put(NAVIGATIONAL_URL, String.format("%s/fromPlugin?instanceId=%s",readValue.get(NAVIGATIONAL_URL).asText(), readValue.get("id").asText()));
				outputs.put(APPROVAL_URL, String.format("%s/fromPlugin?instanceId=%s",readValue.get(NAVIGATIONAL_URL).asText(), readValue.get("id").asText()));
			}

			return TaskResult.builder(ExecutionStatus.SUCCEEDED)
					.context(contextMap)
					.outputs(outputs)
					.build();

		} catch (Exception e) {
			logger.error("Error occurred while processing approval gate : {}", e);
			outputs.put(EXCEPTION, String.format("Error occurred while processing, %s", e));
			outputs.put(TRIGGER, FAILED);
			outputs.put(STATUS, REJECTED);
			return TaskResult.builder(ExecutionStatus.TERMINAL)
					.context(contextMap)
					.outputs(outputs)
					.build();
		}  finally {
			if (httpClient != null) {
				try {
					httpClient.close();
				} catch (IOException e) {
					logger.error("Error while closing client connection : {}", e);
				}
			}
		}
	}

	private String preparePayload(Map<String, Object> parameterContext, String executionId) throws JsonProcessingException {

		ObjectNode finalJson = objectMapper.createObjectNode();

		finalJson.put("approvalCallbackURL", "http://oes-platform:8095/callbackurl");
		finalJson.put("rejectionCallbackURL", "http://oes-platform:8095/rejectionbackurl");
		finalJson.put("executionId", executionId);
		String connectorJson = objectMapper.writeValueAsString(parameterContext.get(CONNECTORS));
		ArrayNode connectorNode = (ArrayNode) objectMapper.readTree(connectorJson);
		ArrayNode toolConnectorPayloads = objectMapper.createArrayNode();
		connectorNode.forEach(connector ->
				setParameters(toolConnectorPayloads, connector)
		);
		finalJson.set(TOOL_CONNECTOR_PARAMETERS, toolConnectorPayloads);
		finalJson.set("customConnectorData", objectMapper.createArrayNode());
		ArrayNode payloadConstraintNode = objectMapper.createArrayNode();
		if (parameterContext.get("gateSecurity") != null) {
			String gateSecurityPayload = objectMapper.writeValueAsString(parameterContext.get("gateSecurity"));
			ArrayNode securityNode = (ArrayNode) objectMapper.readTree(gateSecurityPayload);
			securityNode.forEach(secNode -> {
				ArrayNode valuesNode = (ArrayNode)secNode.get("values");
				for (JsonNode jsonNode : valuesNode) {
					if (jsonNode.get("label") != null && !jsonNode.get("label").isNull() && !jsonNode.get("label").asText().isEmpty()) {
						payloadConstraintNode.add(objectMapper.createObjectNode()
								.put(jsonNode.get("label").asText(), jsonNode.get("value").isNull() ? null : jsonNode.get("value").asText()));
					}
				}
			});
		}
		finalJson.set(PAYLOAD_CONSTRAINT, payloadConstraintNode);
		logger.debug("Payload string to trigger approval : {}", finalJson);

		return objectMapper.writeValueAsString(finalJson);
	}

	private void setParameters(ArrayNode toolConnectorPayloads, JsonNode connector) {

		ArrayNode supporetedParams = (ArrayNode) connector.get("supportedParams");
		boolean hasType =  supporetedParams.get(0).has(TYPE);
		if(hasType) {
			dynamicPayload(toolConnectorPayloads, connector, supporetedParams);
		} else {
			nonDynamic(toolConnectorPayloads, connector);
		}
	}

	private void nonDynamic(ArrayNode toolConnectorPayloads, JsonNode connector) {
		String connectorType = connector.get(CONNECTOR_TYPE).asText();
		switch (connectorType) {
			case JIRA:
				singlePayload(toolConnectorPayloads, connector, JIRA, JIRA_TICKET_NO);
				break;
			case AUTOPILOT:
				singlePayload(toolConnectorPayloads, connector, AUTOPILOT, CANARY_ID);
				break;
			case AQUAWAVE:
				singlePayload(toolConnectorPayloads, connector, AQUAWAVE, IMAGE_ID);
				break;
			case APPSCAN:
				singlePayload(toolConnectorPayloads, connector, APPSCAN, REPORT_ID);
				break;
			case SONARQUBE:
				singlePayload(toolConnectorPayloads, connector, SONARQUBE, PROJECT_KEY);
				break;
			case JFROG:
				singlePayload(toolConnectorPayloads, connector, JFROG, WATCH_NAME);
				break;
			case GIT:
				gitPayload(toolConnectorPayloads, connector);
				break;
			case JENKINS:
				jenkinsPayload(toolConnectorPayloads, connector);
				break;
			case BAMBOO:
				bambooPayload(toolConnectorPayloads, connector);
				break;
			case PRISMACLOUD:
				singlePayload(toolConnectorPayloads, connector, PRISMACLOUD, IMAGE_ID);
				break;
			case BITBUCKET:
				bitBucket(toolConnectorPayloads, connector);
				break;
			case SERVICENOW:
				singlePayload(toolConnectorPayloads, connector, SERVICENOW, NUMBER);
				break;
			case ARTIFACTORY:
				artifactoryBucket(toolConnectorPayloads, connector);
				break;
		}
	}

	private void dynamicPayload(ArrayNode toolConnectorPayloads, JsonNode connector, ArrayNode supporetedParams) {
		ArrayNode valuesNode = (ArrayNode) connector.get(VALUES);
		ArrayNode parameterArrayNode = objectMapper.createArrayNode();
		valuesNode.forEach(a -> {
			if (a != null) {
				Iterator<String> fieldNames = a.fieldNames();
				ObjectNode payloadObject = objectMapper.createObjectNode();
				while (fieldNames.hasNext()) {
					String key = fieldNames.next();
					if (a.get(key) != null && ! a.get(key).asText().isEmpty()) {
						supporetedParams.forEach(sp -> {
							String name = sp.get("name").asText();
							if (name.equals(key)) {
								String type = sp.get(TYPE).asText();
								if(type.equalsIgnoreCase("string")) {
									payloadObject.put(key, a.get(key).asText().trim());
								} else {
									ArrayNode paramsNode = objectMapper.createArrayNode();
									Arrays.asList(a.get(key).asText().split(",")).forEach(tic ->
											paramsNode.add(tic.trim())
									);
									payloadObject.set(key, paramsNode);
								}
							}
						});
					}
				}

				if(payloadObject.size() > 0) {
					parameterArrayNode.add(payloadObject);
				}
			}
		});

		if (parameterArrayNode != null && parameterArrayNode.size() >= 1) {
			toolConnectorPayloads.add(objectMapper.createObjectNode().put(CONNECTOR_TYPE, connector.get(CONNECTOR_TYPE).asText()).set(PARAMETERS, parameterArrayNode));
		}
	}

	private void artifactoryBucket(ArrayNode toolConnectorPayloads, JsonNode connector) {
		ObjectNode artifactoryNode = objectMapper.createObjectNode();
		artifactoryNode.put(CONNECTOR_TYPE, ARTIFACTORY);
		ArrayNode parameterArrayNode = objectMapper.createArrayNode();
		ArrayNode valuesNode = (ArrayNode) connector.get(VALUES);
		valuesNode.forEach(gitNode -> {
			if (gitNode != null && gitNode.get(REPOSITORY_PATH) != null && ! gitNode.get(REPOSITORY_PATH).asText().isEmpty()) {

				parameterArrayNode.add(objectMapper.createObjectNode().put(REPOSITORY_PATH, gitNode.get(REPOSITORY_PATH).asText().trim()));
			}
		});

		if (parameterArrayNode != null && parameterArrayNode.size() >= 1) {
			artifactoryNode.set(PARAMETERS, parameterArrayNode);
			toolConnectorPayloads.add(artifactoryNode);
		}
	}

	private void bitBucket(ArrayNode toolConnectorPayloads, JsonNode connector) {
		ObjectNode gitObjectNode = objectMapper.createObjectNode();
		gitObjectNode.put(CONNECTOR_TYPE, BITBUCKET);
		ArrayNode parameterArrayNode = objectMapper.createArrayNode();
		ArrayNode valuesNode = (ArrayNode) connector.get(VALUES);
		valuesNode.forEach(gitNode -> {
			if (gitNode != null && gitNode.get(REPOSITORY_NAME) != null && ! gitNode.get(REPOSITORY_NAME).asText().isEmpty()
					&& gitNode.get(COMMIT_ID) != null && ! gitNode.get(COMMIT_ID).asText().isEmpty()) {

				parameterArrayNode.add(objectMapper.createObjectNode().put(REPOSITORY_NAME, gitNode.get(REPOSITORY_NAME).asText()).put(COMMIT_ID, gitNode.get(COMMIT_ID).asText()));
			}
		});

		if (parameterArrayNode != null && parameterArrayNode.size() >= 1) {
			gitObjectNode.set(PARAMETERS, parameterArrayNode);
			toolConnectorPayloads.add(gitObjectNode);
		}
	}

	private void bambooPayload(ArrayNode toolConnectorPayloads, JsonNode connector) {
		ObjectNode bambooObjectNode = objectMapper.createObjectNode();
		bambooObjectNode.put(CONNECTOR_TYPE, BAMBOO);
		ArrayNode parameterArrayNode = objectMapper.createArrayNode();
		ArrayNode valuesNode = (ArrayNode) connector.get(VALUES);
		valuesNode.forEach(bambooNode -> {
			if (bambooNode != null && bambooNode.get(PROJECT_NAME) != null && ! (bambooNode.get(PROJECT_NAME).asText()).isEmpty()
					&& bambooNode.get(PLAN_NAME) != null && ! bambooNode.get(PLAN_NAME).asText().isEmpty()
					&& bambooNode.get(BUILD_NUMBER) != null && ! bambooNode.get(BUILD_NUMBER).asText().isEmpty()) {
				parameterArrayNode.add(objectMapper.createObjectNode()
						.put(PROJECT_NAME, bambooNode.get(PROJECT_NAME).asText().trim())
						.put(PLAN_NAME, bambooNode.get(PLAN_NAME).asText().trim())
						.put(BUILD_NUMBER, bambooNode.get(BUILD_NUMBER) != null ?  bambooNode.get(BUILD_NUMBER).asText().trim() : ""));
			}
		});

		if (parameterArrayNode != null && parameterArrayNode.size() >= 1) {
			bambooObjectNode.set(PARAMETERS, parameterArrayNode);
			toolConnectorPayloads.add(bambooObjectNode);
		}
	}

	private void jenkinsPayload(ArrayNode toolConnectorPayloads, JsonNode connector) {
		ObjectNode jenkinsObjectNode = objectMapper.createObjectNode();
		jenkinsObjectNode.put(CONNECTOR_TYPE, JENKINS);
		ArrayNode parameterArrayNode = objectMapper.createArrayNode();
		ArrayNode valuesNode = (ArrayNode) connector.get(VALUES);
		valuesNode.forEach(jenkinsNode -> {
			if (jenkinsNode != null && jenkinsNode.get(JOB) != null && ! (jenkinsNode.get(JOB).asText()).isEmpty()
					&& jenkinsNode.get(BUILD_ID) != null && ! (jenkinsNode.get(BUILD_ID).asText()).isEmpty()) {
				parameterArrayNode.add(objectMapper.createObjectNode()
						.put(JOB, jenkinsNode.get(JOB).asText().trim())
						.put(BUILD_ID, jenkinsNode.get(BUILD_ID).asText().trim())
						.put(ARTIFACT, jenkinsNode.get(ARTIFACT) != null ?  jenkinsNode.get(ARTIFACT).asText().trim() : ""));
			}
		});

		if (parameterArrayNode != null && parameterArrayNode.size() >= 1) {
			jenkinsObjectNode.set(PARAMETERS, parameterArrayNode);
			toolConnectorPayloads.add(jenkinsObjectNode);
		}
	}

	private void gitPayload(ArrayNode toolConnectorPayloads, JsonNode connector) {
		ObjectNode gitObjectNode = objectMapper.createObjectNode();
		gitObjectNode.put(CONNECTOR_TYPE, GIT);
		ArrayNode parameterArrayNode = objectMapper.createArrayNode();
		ArrayNode valuesNode = (ArrayNode) connector.get(VALUES);
		valuesNode.forEach(gitNode -> {
			if (gitNode != null && gitNode.get(REPO) != null && ! gitNode.get(REPO).asText().isEmpty()
					&& gitNode.get(COMMIT_ID) != null && ! gitNode.get(COMMIT_ID).asText().isEmpty()) {
				ArrayNode commitIds = objectMapper.createArrayNode();
				Arrays.asList(gitNode.get(COMMIT_ID).asText().split(",")).forEach(a ->
						commitIds.add(a.trim())
				);

				parameterArrayNode.add(objectMapper.createObjectNode().put(REPO, gitNode.get(REPO).asText()).set(COMMIT_ID, commitIds));
			}
		});

		if (parameterArrayNode != null && parameterArrayNode.size() >= 1) {
			gitObjectNode.set(PARAMETERS, parameterArrayNode);
			toolConnectorPayloads.add(gitObjectNode);
		}
	}

	private void singlePayload(ArrayNode toolConnectorPayloads, JsonNode connector, String type, String param) {
		ObjectNode singleObjectNode = objectMapper.createObjectNode();
		singleObjectNode.put(CONNECTOR_TYPE, type);
		ArrayNode paramsNode = objectMapper.createArrayNode();
		ArrayNode valuesNode = (ArrayNode) connector.get(VALUES);
		valuesNode.forEach(a -> {
			if (a != null) {
				Arrays.asList(a.get(param).asText().split(",")).forEach(tic ->
						paramsNode.add(tic.trim())
				);
			}
		});
		singleObjectNode.set(PARAMETERS, objectMapper.createArrayNode().add(objectMapper.createObjectNode().set(param, paramsNode)));
		toolConnectorPayloads.add(singleObjectNode);
	}



	private String getTriggerURL(StageExecution stage, Map<String, Object> outputs) throws UnsupportedEncodingException {

		String triggerEndpoint = constructGateEnpoint(stage);
		logger.info("triggerEndpoint : {}", triggerEndpoint);
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

			logger.info("STATUS CODE: {}, RESPONSE : {}", response.getStatusLine().getStatusCode(), registerResponse);
			if (response.getStatusLine().getStatusCode() != 200) {
				outputs.put(EXCEPTION, String.format("Failed to get the trigger endpoint with Response :: %s", registerResponse));
				outputs.put(TRIGGER, FAILED);
				outputs.put(STATUS, REJECTED);
				return null;
			}

			ObjectNode readValue = objectMapper.readValue(registerResponse, ObjectNode.class);
			String triggerUrl = readValue.get("gateUrl").isNull() ? null : readValue.get("gateUrl").asText();
			if (triggerUrl == null || triggerUrl.isEmpty() || triggerUrl.equalsIgnoreCase("null")) {
				outputs.put("Reason", String.format("Failed to get trigger endpoint with response :: %s", registerResponse));
				outputs.put(EXCEPTION, "Failed to get trigger endpoint. Please resave the stage before execution");
				outputs.put(TRIGGER, FAILED);
				outputs.put(STATUS, REJECTED);
				return null;
			}
			return triggerUrl;
		} catch (Exception e) {
			logger.error("Failed to execute approval gate", e);
			outputs.put(EXCEPTION, String.format("Error occurred while getting trigger endpoint, %s", e));
			outputs.put(TRIGGER, FAILED);
			outputs.put(STATUS, REJECTED);
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
		return String.format("%s/platformservice/v6/applications/%s/pipeline/%s/reference/%s/gates/%s?type=approval",
				isdGateUrl.endsWith("/") ? isdGateUrl.substring(0, isdGateUrl.length() - 1) : isdGateUrl,
				stage.getExecution().getApplication(), encodeString(stage.getExecution().getName()), stage.getRefId(),
				encodeString(stage.getName()));
	}


	private ApplicationModel getAppDetails(StageExecution stage) throws Exception {

		try (CloseableHttpClient httpClient = HttpClients.createDefault()){
			String appDetailsUrl = getAppDetailsUrl(stage);
			logger.debug("Invoking the URL : {} to fetch the app details", appDetailsUrl);
			HttpGet request = new HttpGet(appDetailsUrl);
			request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			request.setHeader(Constants.X_SPINNAKER_USER, stage.getExecution().getAuthentication().getUser());

			ApplicationModel applicationModel = gson.fromJson(EntityUtils.toString(httpClient.execute(request).getEntity()), ApplicationModel.class);
			logger.debug("Application details response : {}", applicationModel);
			return applicationModel;
		}
	}

	@NotNull
	private String getAppDetailsUrl(StageExecution stage) {
		return getIsdGateUrl()
				+ Constants.GET_APPDETAILS_URL.replace("{applicationName}", stage.getExecution().getApplication()).replace("{pipelineName}", stage.getExecution().getName()) + "&refId=" + stage.getRefId() + "&gateName=" + stage.getName() + "&gateType=" + stage.getType();
	}

	private String getIsdGateUrl(){
		return isdGateUrl.endsWith("/") ? isdGateUrl.substring(0, isdGateUrl.length() - 1) : isdGateUrl;
	}

	private ApplicationModel correctTheAppDetails(@NotNull StageExecution stage) throws Exception {
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

	private GateModel createApprovalGate(StageExecution stage, ApplicationModel applicationModel) throws Exception{

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			String createGateUrl = getCreateGateUrl(applicationModel);
			logger.debug("Create Approval GATE url : {}", createGateUrl);
			HttpPost request = new HttpPost(createGateUrl);

			GateModel gateModel = new GateModel();
			String username = stage.getExecution().getAuthentication().getUser();

			String stringParam = gson.toJson(stage.getContext().get("parameters"), Map.class);
			logger.debug("Approval GATE parameters : {}", stringParam);

			JsonObject parameters = gson.fromJson(stringParam, JsonObject.class);


			gateModel.setApplicationId(applicationModel.getAppId().toString());
			gateModel.setGateName(stage.getName());
			gateModel.setDependsOn(new ArrayList<>(stage.getRequisiteStageRefIds()));
			gateModel.setGateType(stage.getType());
			gateModel.setRefId(stage.getRefId());
			gateModel.setPipelineId(applicationModel.getPipelineId());

			//Approval Gate specific details start
			gateModel.setApprovalGateId(0);
			gateModel.setIsAutomatedApproval(isAutomatedApproval(parameters));

			if (gateModel.getIsAutomatedApproval()) {
				gateModel.setApprovalGatePolicies(getApprovalGatePolicies(parameters));
			}


			//Approval Gate specific details end

			gateModel.setEnvironmentId(getEnvironmentId(parameters));
			gateModel.setPayloadConstraint(getPayloadConstraints(parameters));

			request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			request.setHeader(Constants.X_SPINNAKER_USER, username);
			request.setHeader(HttpHeaders.ORIGIN, Constants.PLUGIN_NAME);

			String body = objectMapper.writeValueAsString(gateModel);
			request.setEntity(new StringEntity(body));

			logger.debug("Create Approval GATE request body : {}", body);

			CloseableHttpResponse response = httpClient.execute(request);
			if (response.getStatusLine().getStatusCode() == HttpStatus.OK.value()){
				logger.info("Successfully created Approval GATE");
			}

			GateModel createGateResponse = gson.fromJson(EntityUtils.toString(response.getEntity()), GateModel.class);
			logger.debug("Create Approval GATE response body : {}", createGateResponse);

			Integer approvalGateId = createGateResponse.getApprovalGateId();
			Integer platformGateId = createGateResponse.getId();
			postApprovalGroups(parameters, platformGateId, username);
			postConnectorAccountsDetailForApprovalGate(parameters, approvalGateId.longValue(), username);
			CloseableHttpResponse updateGateResponse = updateGate(platformGateId,applicationModel.getPipelineId(), createGateResponse, username);
			if (updateGateResponse.getStatusLine().getStatusCode() == HttpStatus.OK.value()){
				logger.info("Successfully updated Approval Gate with tool connectors");
			}

			return createGateResponse;
		}
	}

	private Set<GateModel.ApprovalGatePolicy> getApprovalGatePolicies(JsonObject parameters) {
		Type approvalType = new TypeToken<HashSet<GateModel.ApprovalGatePolicy>>() {
		}.getType();
		if (parameters.has("automatedApproval")) {
			return gson.fromJson(parameters.getAsJsonArray("automatedApproval"), approvalType);
		}
		return Collections.emptySet();

	}

	@NotNull
	private String getCreateGateUrl(ApplicationModel applicationModel) {
		return getIsdGateUrl() + Constants.CREATE_GATE_URL.replace("{pipelineId}", applicationModel.getPipelineId().toString());
	}

	private void postApprovalGroups(JsonObject parameters, int gateId, String username) throws Exception {
		List<UserGroupPermission> userGroupPermissions = new ArrayList<>();
		List<UserGroup> userGroups = new ArrayList<>();
		List<String> permissionIds = List.of("approve_gate");
		JsonArray approvalGroups = parameters.getAsJsonArray("approvalGroups");
		UserGroupPermission userGroupPermission = new UserGroupPermission();

		if (approvalGroups != null && !approvalGroups.isJsonNull() && approvalGroups.size() > 0) {
			for (JsonElement element : approvalGroups) {
				UserGroup userGroup = new UserGroup();

				JsonObject userGroupJsonObject = element.getAsJsonObject();
				userGroup.setUserGroupId(userGroupJsonObject.get("userGroupId").getAsInt());
				userGroup.setUserGroupName(userGroupJsonObject.get("userGroupName").getAsString().trim());
				userGroup.setAdmin(userGroupJsonObject.get("isAdmin").getAsBoolean());
				userGroup.setSuperAdmin(userGroupJsonObject.get("isSuperAdmin").getAsBoolean());
				userGroups.add(userGroup);
				userGroupPermission.setUserGroupNames(userGroups);
				userGroupPermission.setPermissionIds(permissionIds);
			}
			userGroupPermissions.add(userGroupPermission);
			GroupPermission approvalUserGroupPermission = new GroupPermission();
			approvalUserGroupPermission.setUserGroups(userGroupPermissions);
			CloseableHttpResponse userGroupResponse = updateResourceUserGroupPermissionsForApproval(username, gateId, approvalUserGroupPermission);

			logger.debug("Response received from update Resource User Group Permissions For Approval : {}", EntityUtils.toString(userGroupResponse.getEntity()));

			if (userGroupResponse.getStatusLine().getStatusCode() == HttpStatus.OK.value()) {
				logger.info("Successfully updated the approval user group permission for gate id : {}", gateId);
			}
		}

	}

	private CloseableHttpResponse updateResourceUserGroupPermissionsForApproval(String username, Integer resourceId, GroupPermission groupPermission) throws Exception{
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			String updateResourceUserGroupPermissionUrl = getUpdateResourceUserGroupPermissionUrl(username, resourceId);
			logger.debug("updateResourceUserGroupPermissionUrl : {}", updateResourceUserGroupPermissionUrl);
			HttpPut request = new HttpPut(updateResourceUserGroupPermissionUrl);
			String body = objectMapper.writeValueAsString(groupPermission);
			request.setEntity(new StringEntity(body));

			logger.debug("Request body to update Resource User Group Permissions For Approval : {}", body);

			request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			request.setHeader(Constants.X_SPINNAKER_USER, username);
			request.setHeader(HttpHeaders.ORIGIN, Constants.PLUGIN_NAME);

			return httpClient.execute(request);
		}
	}

	@NotNull
	private String getUpdateResourceUserGroupPermissionUrl(String username, Integer resourceId) {
		return getIsdGateUrl() +
				Constants.UPDATE_USER_GROUP_URL.replace("{username}", username).replace("{resourceId}", resourceId.toString()+"?featureType=APPROVAL_GATE");
	}

	private CloseableHttpResponse updateGate(Integer gateId, Integer pipelineId, GateModel gateModel, String username) throws Exception {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			String updateGateUrl = getUpdateGateUrl(gateId, pipelineId);
			logger.debug("update approval gate url : {}", updateGateUrl);
			HttpPut request = new HttpPut(updateGateUrl);
			String body = objectMapper.writeValueAsString(gateModel);
			request.setEntity(new StringEntity(body));

			logger.debug("update approval gate request body : {}", body);

			request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			request.setHeader(Constants.X_SPINNAKER_USER, username);
			request.setHeader(HttpHeaders.ORIGIN, Constants.PLUGIN_NAME);

			return httpClient.execute(request);
		}
	}

	@NotNull
	private String getUpdateGateUrl(Integer gateId, Integer pipelineId) {
		return getIsdGateUrl() +
				Constants.UPDATE_GATE_URL.replace("{pipelineId}", pipelineId.toString()).replace("{gateId}", gateId.toString());
	}

	private void postConnectorAccountsDetailForApprovalGate(JsonObject parameters, Long approvalGateId, String username) throws Exception {
		//Post connector details, if available
		if (parameters.has("selectedConnectors")) {
			JsonObject selectedConnectors = parameters.getAsJsonArray("selectedConnectors").get(0).getAsJsonObject();
			if (selectedConnectors.has("values") && selectedConnectors.getAsJsonArray("values").size() > 0) {
				JsonArray connectorValues = selectedConnectors.getAsJsonArray("values");
				for (JsonElement e : connectorValues) {
					JsonObject connectorDetails = e.getAsJsonObject();
					if(!connectorDetails.has("account") || StringUtils.isBlank(connectorDetails.get("account").getAsString())) {
						break;
					}
					Map<String, String> connectorAccountDetails = new HashMap<>();
					connectorAccountDetails.put("datasourceName", connectorDetails.get("account").getAsString().trim());

					CloseableHttpResponse connectorAccountDetailsResponse = updateApprovalGatesIdToolConnectorTemplate(connectorAccountDetails, approvalGateId, username);

					logger.debug("connectorAccountDetailsResponse : {}", EntityUtils.toString(connectorAccountDetailsResponse.getEntity()));

					if (connectorAccountDetailsResponse.getStatusLine().getStatusCode() == HttpStatus.OK.value()) {
						logger.info("Successfully updated the approval gate with connector details for account name {} and connector type {} for approval gate id {} ",
								connectorDetails.get("account").getAsString().trim(), connectorDetails.get("connector").getAsString(), approvalGateId);
					}
				}
			}
		}
	}

	private CloseableHttpResponse updateApprovalGatesIdToolConnectorTemplate(Map<String, String> connectorAccountDetails, Long approvalGateId, String username) throws Exception {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()){
			String updateToolConnectorsUrl = getUpdateToolConnectorsUrl(approvalGateId);
			logger.debug("updateToolConnectorsUrl : {}", updateToolConnectorsUrl);
			HttpPut request = new HttpPut(updateToolConnectorsUrl);

			request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			request.setHeader(Constants.X_SPINNAKER_USER, username);
			request.setHeader(HttpHeaders.ORIGIN, Constants.PLUGIN_NAME);

			String body = objectMapper.writeValueAsString(connectorAccountDetails);
			request.setEntity(new StringEntity(body));
			logger.debug("updateToolConnectors API request body : {}", body);

			return httpClient.execute(request);
		}
	}

	@NotNull
	private String getUpdateToolConnectorsUrl(Long approvalGateId) {
		return getIsdGateUrl()
				+ Constants.UPDATE_TOOL_CONNECTOR_URL.replace("{id}", approvalGateId.toString());
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

	private boolean isAutomatedApproval(JsonObject parameters) {
		boolean isAutomatedApproval;
		if (parameters.has("isAutomatedApproval")) {
			isAutomatedApproval = parameters.get("isAutomatedApproval").getAsBoolean();
		} else {
			isAutomatedApproval = false;
		}
		return isAutomatedApproval;
	}

	private String encodeString(String value) throws UnsupportedEncodingException {
		return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
	}
}
