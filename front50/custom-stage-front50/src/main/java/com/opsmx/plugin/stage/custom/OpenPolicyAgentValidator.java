package com.opsmx.plugin.stage.custom;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import com.google.gson.*;
import com.netflix.spinnaker.front50.api.validator.ValidatorErrors;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.api.validator.PipelineValidator;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import com.netflix.spinnaker.kork.web.exceptions.ValidationException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Extension
@Component
@ComponentScan("com.opsmx.plugin.stage.custom")
public class OpenPolicyAgentValidator implements PipelineValidator, SpinnakerExtensionPoint {

	private final Logger logger = LoggerFactory.getLogger(OpenPolicyAgentValidator.class);
	private static final String RESULT = "result";

	private OpaConfigProperties opaConfigProperties;

	@Autowired
	public OpenPolicyAgentValidator(@Qualifier("opaConfigProperties")OpaConfigProperties opaConfigProperties) {
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
		logger.debug("Start of the Policy Validation");
		if (!opaConfigProperties.isEnabled()) {
			logger.info("OPA not enabled, returning");
			logger.debug("End of the Policy Validation");
			return;
		}
		String finalInput = null;
		Response httpResponse;
		try {
			// Form input to opa
			finalInput = getOpaInput(pipeline);
			logger.debug("Verifying {} with OPA", finalInput);
			/* build our request to OPA */
			RequestBody requestBody = RequestBody.create(JSON, finalInput);
			logger.debug("OPA endpoint : {}", opaConfigProperties.getUrl());
			String opaStringResponse;

			/* fetch the response from the spawned call execution */
			if (!opaConfigProperties.getStaticpolicies().isEmpty()) {
				for(OpaConfigProperties.Policy policy: opaConfigProperties.getStaticpolicies()){
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
		logger.debug("End of the Policy Validation");
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
		logger.debug("Start of the getOpaInput");
		String application;
		String pipelineName;
		String finalInput = null;
		JsonObject newPipeline = pipelineToJsonObject(pipeline);
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

	private JsonObject pipelineToJsonObject(Pipeline pipeline) {
		logger.debug("Start of the pipelineToJsonObject");
		try {
			String pipelineStr = gson.toJson(pipeline, Pipeline.class);
			logger.debug("End of the pipelineToJsonObject");
			return gson.fromJson(pipelineStr, JsonObject.class);
		} catch (JsonParseException e) {
			e.printStackTrace();
			logger.error("Exception occure while converting the input pipline to Json :{}", e);
			logger.debug("End of the pipelineToJsonObject");
			throw new ValidationException("Converstion Failed while converting the input pipline to Json:" + e.toString(), null);
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