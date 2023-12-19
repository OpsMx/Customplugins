package com.opsmx.plugin.stage.custom;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import com.netflix.spinnaker.front50.api.validator.ValidatorErrors;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.api.validator.PipelineValidator;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import com.netflix.spinnaker.kork.web.exceptions.ValidationException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Extension
@Component
@ComponentScan("com.opsmx.plugin.stage.custom")
public class OpenPolicyAgentValidator implements PipelineValidator, SpinnakerExtensionPoint {

	private final Logger logger = LoggerFactory.getLogger(OpenPolicyAgentValidator.class);
	private static final String RESULT = "result";
	private static final String STATUS = "status";

	private OpaConfigProperties opaConfigProperties;

	public OpenPolicyAgentValidator(OpaConfigProperties opaConfigProperties) {
		this.opaConfigProperties = opaConfigProperties;
	}

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

    @Override
	public void validate(Pipeline pipeline, ValidatorErrors errors) {
		if (!opaConfigProperties.isEnabled()) {
			logger.info("OPA not enabled, returning");
			return;
		}
		String finalInput = null;
		try {
			// Form input to opa
			finalInput = getOpaInput(pipeline);
			logger.debug("Verifying {} with OPA", finalInput);
			/* build our request to OPA */
			RequestBody requestBody = RequestBody.create(JSON, finalInput);
			logger.debug("OPA endpoint : {}", opaConfigProperties.getUrl());
			String opaStringResponse;
			int statusCode = 200;

			/* fetch the response from the spawned call execution */
			if (!opaConfigProperties.getStaticpolicies().isEmpty()) {
				for(OpaConfigProperties.Policy policy: opaConfigProperties.getStaticpolicies()){
					String opaFinalUrl = String.format("%s/%s", opaConfigProperties.getUrl().endsWith("/") ? opaConfigProperties.getUrl().substring(0, opaConfigProperties.getUrl().length() - 1) : opaConfigProperties.getUrl(), policy.getPackageName().startsWith("/") ? policy.getPackageName().substring(1) : policy.getPackageName());
					logger.debug("opaFinalUrl: {}", opaFinalUrl);
					Request req = doPost(opaFinalUrl, requestBody);
					logger.debug("opaFinalUrl: {}", opaFinalUrl);
					Map<String, Object> responseObject = getOPAResponse(opaFinalUrl, req);
					opaStringResponse = String.valueOf(responseObject.get(RESULT));
					statusCode = Integer.valueOf(responseObject.get(STATUS).toString());
					validateOPAResponse(opaStringResponse, statusCode);
				}
			}
		} catch (IOException e) {
			logger.error("Communication exception for OPA at {}: {}", opaConfigProperties.getUrl(), e.toString());
			throw new ValidationException(e.toString(), null);
		}
	}
	private void validateOPAResponse(String opaStringResponse, int statusCode) {
		logger.debug("OPA response: {}", opaStringResponse);
		logger.debug("proxy enabled : {}, statuscode : {}, opaResultKey : {}", opaConfigProperties.isProxy(), statusCode, opaConfigProperties.getResultKey());
		if (opaConfigProperties.isProxy()) {
			if (statusCode == 401 ) {
				JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
				StringBuilder denyMessage = new StringBuilder();
				extractDenyMessage(opaResponse, denyMessage);
				if (StringUtils.isNotBlank(denyMessage)) {
					throw new ValidationException(denyMessage.toString(), null);
				} else {
					throw new ValidationException("There is no '" + opaConfigProperties.getResultKey() + "' field in the OPA response", null);
				}
			} else if (statusCode != 200 ) {
				throw new ValidationException(opaStringResponse, null);
			}
		} else {
			if (statusCode == 401 ) {
				JsonObject opaResponse = gson.fromJson(opaStringResponse, JsonObject.class);
				StringBuilder denyMessage = new StringBuilder();
				extractDenyMessage(opaResponse, denyMessage);
				if (StringUtils.isNotBlank(denyMessage)) {
					throw new ValidationException(denyMessage.toString(), null);
				} else {
					throw new ValidationException("There is no '" + opaConfigProperties.getResultKey() + "' field in the OPA response", null);
				}
			} else if (statusCode != 200 ) {
				throw new ValidationException(opaStringResponse, null);
			}
		}

	}
	private void extractDenyMessage(JsonObject opaResponse, StringBuilder messagebuilder) {
		Set<Entry<String, JsonElement>> fields = opaResponse.entrySet();
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
			}else if (field.getValue().isJsonObject()) {
				extractDenyMessage(field.getValue().getAsJsonObject(), messagebuilder);
			} else if (field.getValue().isJsonArray()){
				field.getValue().getAsJsonArray().forEach(obj -> {
					extractDenyMessage(obj.getAsJsonObject(), messagebuilder);
				});
			}
		});
	}

	private String getOpaInput(Pipeline pipeline) {
		String application;
		String pipelineName;
		String finalInput = null;
		JsonObject newPipeline = pipelineToJsonObject(pipeline);
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

	private JsonObject pipelineToJsonObject(Pipeline pipeline) {
		String pipelineStr = gson.toJson(pipeline, Pipeline.class);
		return gson.fromJson(pipelineStr, JsonObject.class);
	}

	private Request doPost(String url, RequestBody requestBody) throws IOException {
		return (new Request.Builder()).url(url).post(requestBody).build();
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