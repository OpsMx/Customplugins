package com.opsmx.plugin.stage.custom;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
//import com.netflix.spinnaker.fiat.model.Authorization;
//import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.model.Application;

import groovy.util.logging.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
@ComponentScan({"com.opsmx.plugin.stage.custom"})
@Extension
@Component
@Slf4j
public class RBACValidationTask implements Task {

	private static final String APPLICATION2 = "application";

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

	private final Gson gson = new Gson();

	@Autowired
	private static final ObjectMapper mapper = new ObjectMapper();

	private final Optional<FiatService> fiatService;

	private final FiatStatus fiatStatus;
	
	/* OPA spits JSON */
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private final OkHttpClient opaClient = new OkHttpClient();	

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public RBACValidationTask(Optional<FiatService> fiatService, FiatStatus fiatStatus) {
		this.fiatService = fiatService;
		this.fiatStatus = fiatStatus;
	}

	@Override
	public TaskResult execute(StageExecution stage) {
		if (!isOpaEnabled) {
			logger.info("OPA not enabled, returning");
			return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
		}
		
		Application application = mapper.convertValue(stage.getContext().get("application"), Application.class);

		String finalInput = null;
		Response httpResponse;

		try {
			List<String> groupList = new ArrayList<String>();
			if (fiatStatus.isEnabled() && fiatService.isPresent()) {
				UserPermission.View userPermission = fiatService.get().getUserPermission(stage.getExecution().getAuthentication().getUser());
				Set<Role.View> roles = userPermission.getRoles();
				roles.forEach(role -> {
					groupList.add(role.getName());
				});
			}

			if (stage.getType().equalsIgnoreCase("CreateApplication")) {
				finalInput = getOpaInput(application, "createApp", groupList, stage.getExecution().getAuthentication().getUser());
			} else if (stage.getType().equalsIgnoreCase("UpdateApplication")){
				finalInput = getOpaInput(application, "updateApp", groupList, stage.getExecution().getAuthentication().getUser());
			} else {
				finalInput = getOpaInput(application, "deleteApp", groupList, stage.getExecution().getAuthentication().getUser());
			}
			

			logger.debug("Verifying {} with OPA", finalInput);

			/* build our request to OPA */
			RequestBody requestBody = RequestBody.create(JSON, finalInput);
			String opaFinalUrl = String.format("%s/%s", opaUrl.endsWith("/") ? opaUrl.substring(0, opaUrl.length() - 1) : opaUrl, opaPolicyLocation.startsWith("/") ? opaPolicyLocation.substring(1) : opaPolicyLocation);

			logger.info("OPA endpoint : {}", opaFinalUrl);
			String opaStringResponse;

			/* fetch the response from the spawned call execution */
			httpResponse = doPost(opaFinalUrl, requestBody);
			opaStringResponse = httpResponse.body().string();
			logger.debug("OPA response: {}", opaStringResponse);
			logger.debug("proxy enabled : {}, statuscode : {}, opaResultKey : {}", isOpaProxy, httpResponse.code(), opaResultKey);
			if (isOpaProxy) {
				if (httpResponse.code() == 401 ) {
					JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
					StringBuilder denyMessage = new StringBuilder();
					extractDenyMessage(opaResponse, denyMessage);
					if (StringUtils.isNotBlank(denyMessage)) {
						throw new IllegalArgumentException("OpsMx Policy Error(s) - "+ denyMessage.toString());
					} else {
						throw new IllegalArgumentException("OpsMx Policy Error(s) - there is no '" + opaResultKey + "' field in the OPA response", null);
					}
				} else if (httpResponse.code() != 200 ) {
					throw new IllegalArgumentException("OpsMx Policy Error(s) - " + opaStringResponse, null);
				}
			} else {
				if (httpResponse.code() == 401 ) {
					JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
					StringBuilder denyMessage = new StringBuilder();
					extractDenyMessage(opaResponse, denyMessage);
					if (StringUtils.isNotBlank(denyMessage)) {
						throw new IllegalArgumentException("OpsMx Policy Error(s) - "+ denyMessage.toString());
					} else {
						throw new IllegalArgumentException("OpsMx Policy Error(s) - there is no '" + opaResultKey + "' field in the OPA response", null);
					}
				} else if (httpResponse.code() != 200 ) {
					throw new IllegalArgumentException("OpsMx Policy Error(s) - " + opaStringResponse, null);
				}
			}
		} catch (IOException e) {
			logger.error("Failed to connect to OPA server {}: {}", this.opaUrl, e.toString());
			throw new IllegalArgumentException(e.getMessage(), null);
		}

		return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
	}



	private void extractDenyMessage(JsonObject opaResponse, StringBuilder messagebuilder) {
		Set<Entry<String, JsonElement>> fields = opaResponse.entrySet();
		fields.forEach(field -> {
			if (field.getKey().equalsIgnoreCase(opaResultKey)) {
				JsonArray resultKey = field.getValue().getAsJsonArray();
				if (resultKey.size() != 0) {
					resultKey.forEach(result -> {
						if (StringUtils.isNotEmpty(messagebuilder)) {
							messagebuilder.append(", ");
						}
						messagebuilder.append(result.getAsString());
					});
				}
			}else if (field.getValue().isJsonObject()) {
				if (!field.getValue().isJsonPrimitive()) {
					extractDenyMessage(field.getValue().getAsJsonObject(), messagebuilder);
				}
			} else if (field.getValue().isJsonArray()){
				field.getValue().getAsJsonArray().forEach(obj -> {
					if (!obj.isJsonPrimitive()) {
						extractDenyMessage(obj.getAsJsonObject(), messagebuilder);
					}
				});
			}
		});
	}

	
	private String getOpaInput(Application application, String type, List<String> roles, String user) {
		ObjectNode applicationJson = applicationToJson(application, type, roles, user);
		return addWrapper(addWrapper(applicationJson, "app"), "input").toString();
	}
	
   private ObjectNode applicationToJson(Application application, String type, List<String> userGroups, String user) {
		
		ObjectNode appObject = mapper.createObjectNode();
		appObject.put(APPLICATION2, application.name);

		ObjectNode appDetails = mapper.createObjectNode();
		appDetails.put("name", application.name);
		appDetails.put("email", application.email);
		appDetails.put("cloudProviders", application.cloudProviders);
		appDetails.put("description", application.description);
		appDetails.put("platformHealthOnly", application.platformHealthOnly);
		appDetails.put("platformHealthOnlyShowOverride", application.platformHealthOnlyShowOverride);

		ObjectNode permissionNode = mapper.createObjectNode();
		if (application.getPermission() != null)  {
			Permissions permissions = application.getPermission().getPermissions();
			if (permissions != null) {
				EnumSet.allOf( Authorization.class ).forEach(auth -> {
					 ArrayNode roles = mapper.createArrayNode();
					permissions.get(auth).forEach(role -> {
						roles.add(role);
					});
					permissionNode.set(auth.name(), roles);
				});
			} else {
				EnumSet.allOf( Authorization.class ).forEach(auth -> permissionNode.set(auth.name(), mapper.createArrayNode()));
			}
		}
	   ObjectNode userGroupNode = mapper.createObjectNode();
		userGroupNode.put("name", user);
		ArrayNode groupsNode= mapper.createArrayNode();
		if (userGroups != null && !userGroups.isEmpty()) {
			userGroups.forEach(grp -> {
				groupsNode.add(grp);
			});
		}
		userGroupNode.set("groups", groupsNode);
	    appDetails.set("userDetails", userGroupNode);
	    appDetails.set("permissions", permissionNode);

	   ObjectNode jobObjectNode = mapper.createObjectNode();
		jobObjectNode.put("type", type);
		jobObjectNode.set(APPLICATION2, appDetails);
		appObject.set("job", mapper.createArrayNode().add(jobObjectNode));

		return appObject;
	}

	private ObjectNode addWrapper(ObjectNode appObject, String wrapper) {
		ObjectNode input = mapper.createObjectNode();
		input.set(wrapper, appObject);
		return input;
	}

	private Response doPost(String url, RequestBody requestBody) throws IOException {
		Request req = (new Request.Builder()).url(url).post(requestBody).build();
		return getResponse(url, req);
	}

	private Response getResponse(String url, Request req) throws IOException {
		Response httpResponse = this.opaClient.newCall(req).execute();
		ResponseBody responseBody = httpResponse.body();
		if (responseBody == null) {
			throw new IOException("Http call yielded null response!! url:" + url);
		}
		return httpResponse;
	}
}
