package com.opsmx.plugin.pipeline.validate.runtime;

import com.google.gson.Gson;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import com.netflix.spinnaker.orca.api.pipeline.ExecutionPreprocessor;


import javax.annotation.Nonnull;

import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Extension
@Component
@ComponentScan("com.opsmx.plugin.pipeline.runtime")
public class PipelineValidateAgentPreprocessor implements ExecutionPreprocessor, SpinnakerExtensionPoint {

	private final Logger logger = LoggerFactory.getLogger(OpenPolicyAgentPreprocessor.class);

	private final Gson gson = new Gson();

	@Value("${pipeline.validate.enabled:false}")
	private boolean isPipelineValidateEnabled;

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
		logger.debug("Start of the Pipeline Validation");
		logger.debug("input Pipeline :{}", pipeline);
		if (!isPipelineValidateEnabled) {
			logger.debug("Pipeline validation not enabled, returning");
			logger.debug("End of the Pipeline Validation");
			return pipeline;
		}
		try {
           List stages = (ArrayList)pipeline.get("stages");
		   List<String> refIds = new ArrayList<String>();
			List<String> allRequisiteStageRefIds = new ArrayList<String>();
		   for(Object stageObject : stages){
			   Map<String, Object> stage = (HashMap) stageObject;
			   refIds.add(stage.get("refId").toString());
			   List<String> requisiteStageRefIds = (ArrayList)stage.get("requisiteStageRefIds");
			   allRequisiteStageRefIds.addAll(requisiteStageRefIds);
		   }
           validateRefIds(refIds);
		   validateRequisiteStageRefIds(refIds,allRequisiteStageRefIds);
		   validateRefIdAndRequisiteStageRefIds(stages);
		} catch (Exception e) {
			e.printStackTrace();
			//logger.error("Exception occured : {}", e);
			logger.debug("Some thing wrong While validate pipeline input: {}", pipeline);
			logger.debug("End of the Pipeline Validation");
			throw new ValidationException(e.getMessage(), null);
		}
		logger.debug("End of the Pipeline Validation");
		return pipeline;
	}

	private void validateRefIds(List<String> refIds) {
		TreeMap<String, Integer> tmap = new TreeMap<String, Integer>();
		for (String refId : refIds) {
			Integer count = tmap.get(refId);
			tmap.put(refId, (count == null) ? 1 : count + 1);
		}

		for (Map.Entry m : tmap.entrySet()) {
			logger.info("Frequency of " + m.getKey() + " is " + m.getValue());
			if(Integer.valueOf(m.getValue().toString() )> 1){
				logger.debug(" same stage ref Id  :{} used more than one time ",m.getValue());
				throw new ValidationException("Pipeline has been used same stage ref Id  : "+m.getValue()+" more than one time, Update pipeline stage ref Id ", null);
			}
		}
	}

	private void validateRequisiteStageRefIds(List<String> refIds, List<String> allRequisiteStageRefIds) {
		for (String refId : allRequisiteStageRefIds) {
         if(!refIds.contains(refId)){
			 logger.debug("Pipeline stage ref Id  :{} is not available ",refId);
			 throw new ValidationException("Pipeline stage ref Id  :"+refId+" is not available ", null);
		 }
		}
	}

	private void validateRefIdAndRequisiteStageRefIds(List stages) {
		for(Object stageObject : stages){
			Map<String, Object> stage = (HashMap) stageObject;
			String refId = stage.get("refId").toString();
			List<String> requisiteStageRefIds = (ArrayList)stage.get("requisiteStageRefIds");
			if(requisiteStageRefIds.contains(refId)){
				logger.debug("Pipeline stage ref Id  :{} have in requisiteStageRefIds",refId);
				throw new ValidationException("Pipeline stage ref Id  :"+refId+" have in requisiteStageRefIds",null);
			}
		}
	}
}