package com.opsmx.plugin.policy.runtime;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import com.netflix.spinnaker.orca.api.pipeline.ExecutionPreprocessor;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ComponentScan;

import com.netflix.spinnaker.kork.web.exceptions.ValidationException;

import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Extension
@Component
@ComponentScan("com.opsmx.plugin.policy.runtime")
public class OpenPolicyAgentPreprocessor implements ExecutionPreprocessor, SpinnakerExtensionPoint {

	private final Logger logger = LoggerFactory.getLogger(OpenPolicyAgentPreprocessor.class);
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
	public OpenPolicyAgentPreprocessor(@Qualifier("opaConfigProperties") OpaConfigProperties opaConfigProperties) {
        this.opaConfigProperties = opaConfigProperties;
	}
	@Override
	public boolean supports(@Nonnull Map<String, Object> pipeline, @Nonnull Type type){
		logger.debug("ExecutionPreprocessor Type :{}",type);
		if(type.equals(Type.PIPELINE)){
			return true;
		}
		return false;
	}
	@Override
	public Map<String, Object> process(@Nonnull Map<String, Object> pipeline){
		logger.debug("Start of the Policy Validation");
		logger.debug("input Pipeline :{}", pipeline);
		if (!opaConfigProperties.isEnabled()) {
			logger.info("OPA not enabled, returning");
			logger.debug("End of the Policy Validation");
			return pipeline;
		}
		try {
			/*if(isChildPipeline(pipeline)){
				logger.debug("This pipeline is a child pipeline and trigger by parent ");
				logger.debug("End of the Policy Validation");
				return pipeline;
			}*/
			// Form input to opa
			String finalInput = getOpaInput(pipeline);

			logger.debug("Verifying {} with OPA", finalInput);

			/* build our request to OPA */
			RequestBody requestBody = RequestBody.create(JSON, finalInput);

			logger.debug("OPA endpoint : {}", opaConfigProperties.getUrl());
			String opaStringResponse = "{}";
			logger.debug("Policy list :"+opaConfigProperties.getRuntime().size());

			if (!opaConfigProperties.getRuntime().isEmpty()) {
				for (OpaConfigProperties.Policy policy : opaConfigProperties.getRuntime()) {
					String opaFinalUrl = String.format("%s/%s", opaConfigProperties.getUrl().endsWith("/") ? opaConfigProperties.getUrl().substring(0, opaConfigProperties.getUrl().length() - 1) : opaConfigProperties.getUrl(), policy.getPackageName().startsWith("/") ? policy.getPackageName().substring(1) : policy.getPackageName());
					logger.debug("opaFinalUrl: {}", opaFinalUrl);
					Response httpResponse = doPost(opaFinalUrl, requestBody);
					opaStringResponse = httpResponse.body().string();
					logger.debug("OPA response: {}", opaStringResponse);
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
			logger.debug("End of the Policy Validation");
			throw new ValidationException(e.toString(), null);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Exception occured : {}", e);
			logger.error("Some thing wrong While processing the OPA Validation, input : {}", pipeline);
			logger.debug("End of the Policy Validation");
			throw new ValidationException(e.toString(), null);
		}
		logger.debug("End of the Policy Validation");
		return pipeline;
	}

	private boolean isChildPipeline(Map<String, Object> pipeline) {
		if( pipeline.containsKey("trigger") ) {
			Map<String, Object> trigger = (Map<String, Object>) pipeline.get("trigger");
			if (trigger.containsKey("type") && trigger.get("type").toString().equalsIgnoreCase("pipeline") && trigger.containsKey("parentExecution")) {
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

	private String getOpaInput(Map<String, Object> pipeline) {
		logger.debug("Start of the getOpaInput");
		String application;
		String pipelineName;
		try {
			//JsonObject newPipeline = pipelineToJsonObject(pipeline);
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

	private Map<String, Object> addWrapper(Map<String, Object> pipeline, String wrapper) {
		Map<String, Object> input = new HashMap<>();
		input.put(wrapper, pipeline);
		return input;
	}

	private JsonObject pipelineToJsonObject(Map<String, Object> pipeline) {
		logger.debug("Start of the pipelineToJsonObject");
		try {
			String pipelineStr = gson.toJson(pipeline, Map.class);
			logger.debug("End of the pipelineToJsonObject");
			return gson.fromJson(pipelineStr, JsonObject.class);
		}catch (Exception e){
			e.printStackTrace();
			logger.error("Exception occure while converting the input pipline to Json :{}", e);
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