package com.opsmx.plugin.stage.custom;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netflix.spinnaker.kork.plugins.api.PluginComponent;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;

@Extension
@PluginComponent
public class VerificationMonitorTask implements RetryableTask {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public TaskResult execute(StageExecution stage) {
		Map<String, Object> contextMap = new HashMap<>();

		Map<String, Object> outputs = stage.getOutputs();
		String trigger = (String) outputs.getOrDefault(OesConstants.TRIGGER, "NOTYET");

		if (trigger.equals(OesConstants.FAILED)) {
			logger.info("Verification Monitoring terminating because trigger task failed");
			return TaskResult.builder(ExecutionStatus.TERMINAL)
					.context(contextMap)
					.outputs(outputs)
					.build();
		} else if (trigger.equals(OesConstants.SUCCESS)) {
			logger.info("Verification Monitoring started for application : {}, pipeline : {}", stage.getExecution().getApplication(), stage.getExecution().getName());
			String approvalUrl = (String) outputs.get(OesConstants.LOCATION);
			return getVerificationStatus(approvalUrl, stage.getExecution().getAuthentication().getUser(), outputs, (Integer) stage.getContext().get("serviceId"));
		} else {
			logger.info("Verification Monitoring not starting because trigger task not completed");
			return TaskResult.builder(ExecutionStatus.RUNNING)
					.context(contextMap)
					.outputs(outputs)
					.build();
		}
	}

	private TaskResult getVerificationStatus(String canaryUrl, String user, Map<String, Object> outputs, Integer serviceId) {
		HttpGet request = new HttpGet(canaryUrl);

		CloseableHttpClient httpClient = null;
		try {
			request.setHeader("Content-type", "application/json");
			request.setHeader("x-spinnaker-user", user);
			httpClient = HttpClients.createDefault();
			CloseableHttpResponse response = httpClient.execute(request);

			HttpEntity entity = response.getEntity();
			ObjectNode readValue = objectMapper.readValue(EntityUtils.toString(entity), ObjectNode.class);
			String analysisStatus = readValue.get(OesConstants.STATUS).get(OesConstants.STATUS).asText();
			outputs.put(OesConstants.CANARY_REPORTURL,
					String.format("%s/%s", readValue.get(OesConstants.CANARY_RESULT).get(OesConstants.CANARY_REPORTURL).asText(), serviceId));
			if (readValue.get(OesConstants.CANARY_RESULT).get("verificationUrl") != null ) {
				outputs.put("verificationUrl",
						String.format("%s/fromPlugin/%s", readValue.get(OesConstants.CANARY_RESULT).get("verificationUrl").asText(), serviceId));
			}
			if (analysisStatus.equalsIgnoreCase(OesConstants.RUNNING)) {
				return TaskResult.builder(ExecutionStatus.RUNNING)
						.outputs(outputs)
						.build();
			}

			if (analysisStatus.equalsIgnoreCase(OesConstants.CANCELLED)) {
				outputs.put(OesConstants.OVERALL_RESULT, OesConstants.CANCELLED);
				outputs.put(OesConstants.OVERALL_SCORE, 0.0);
				outputs.put(OesConstants.EXCEPTION, "Analysis got cancelled");

				return TaskResult.builder(ExecutionStatus.TERMINAL)
						.outputs(outputs)
						.build();
			}

			Float overAllScore = readValue.get(OesConstants.CANARY_RESULT).get(OesConstants.OVERALL_SCORE).floatValue();
			Float minimumScore = readValue.get(OesConstants.CANARY_CONFIG).get(OesConstants.MINIMUM_CANARY_RESULT_SCORE).floatValue();
			Float maximumScore = readValue.get(OesConstants.CANARY_CONFIG).get(OesConstants.MAXIMUM_CANARY_RESULT_SCORE).floatValue();
			String result = readValue.get(OesConstants.CANARY_RESULT).get(OesConstants.OVERALL_RESULT).asText();

			outputs.put(OesConstants.OVERALL_RESULT, result);
			outputs.put(OesConstants.OVERALL_SCORE, overAllScore);

			if (result.equalsIgnoreCase(OesConstants.FAIL)) {
				outputs.put(OesConstants.EXCEPTION, "Analysis score is below the 'Pass score'");
				return TaskResult.builder(ExecutionStatus.TERMINAL)
						.outputs(outputs)
						.build();
			} else if (result.equalsIgnoreCase(OesConstants.SUCCESS)){
				return TaskResult.builder(ExecutionStatus.SUCCEEDED)
						.outputs(outputs)
						.build();
			}

			else if (result.equalsIgnoreCase(OesConstants.REVIEW) || ( Float.compare(minimumScore, overAllScore) <= 0 &&  Float.compare(overAllScore, maximumScore) < 0 )) {
				outputs.put(OesConstants.REASON, "Analysis score is between 'Marginal Score' and 'Pass score'");
				outputs.put(OesConstants.EXCEPTION, "Analysis score is between 'Marginal Score' and 'Pass score'");
				return TaskResult.builder(ExecutionStatus.TERMINAL)
						.outputs(outputs)
						.build();
			}

			return TaskResult.builder(ExecutionStatus.RUNNING)
					.outputs(outputs)
					.build();

		} catch (Exception e) {
			logger.error("Error occurred while getting anaysis result ", e);
			outputs.put(OesConstants.EXCEPTION, String.format("Error occurred while processing %s", e.getMessage()));
			outputs.put(OesConstants.OVERALL_SCORE, 0.0);
			outputs.put(OesConstants.OVERALL_RESULT, "Fail");
			outputs.put(OesConstants.TRIGGER, OesConstants.FAILED);
			return TaskResult.builder(ExecutionStatus.TERMINAL)
					.outputs(outputs)
					.build();
		} finally {
			if (httpClient != null) {
				try {
					httpClient.close();
				} catch (IOException e) {
				}
			}
		}
	}

	@Override
	public long getBackoffPeriod() {
		return TimeUnit.SECONDS.toMillis(5);
	}

	@Override
	public long getTimeout() {
		return TimeUnit.DAYS.toMillis(1);
	}

}