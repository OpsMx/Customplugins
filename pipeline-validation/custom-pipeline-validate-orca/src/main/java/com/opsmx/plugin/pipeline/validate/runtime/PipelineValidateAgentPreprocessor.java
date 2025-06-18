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
@ComponentScan("com.opsmx.plugin.pipeline.validate.runtime")
public class PipelineValidateAgentPreprocessor implements ExecutionPreprocessor, SpinnakerExtensionPoint {

	private final Logger logger = LoggerFactory.getLogger(PipelineValidateAgentPreprocessor.class);

	private final Gson gson = new Gson();

	@Value("${pipeline.validate.runtime.enabled:false}")
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
		if (!isPipelineValidateEnabled) {
			logger.debug("Pipeline validation is not enabled");
			return pipeline;
		}
		logger.debug("Start of the Pipeline Validation");
		logger.debug("Pipeline validation is enabled");
		logger.debug("input Pipeline :{}", pipeline);
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
		   //duplicate refId
           validateRefIds(refIds);
		   //non existent refId used inside requisiteStageRefIds
		   validateRequisiteStageRefIds(refIds,allRequisiteStageRefIds);
			//self referenced refId
		   validateRefIdAndRequisiteStageRefIds(stages);
		} catch (Exception e) {
			logger.error("Failed to valudate pipeline: {}", pipeline);
			logger.debug("End of the Pipeline Validation");
			throw new ValidationException(e.getMessage(), null);
		}
		logger.debug("End of the Pipeline Validation");
		return pipeline;
	}

	private void validateRefIds(List<String> refIds) {
		TreeMap<String, Integer> tmap = new TreeMap<String, Integer>();
		List<String> duplicateStageRefIds =  new ArrayList<>();
		for (String refId : refIds) {
			Integer count = tmap.get(refId);
			tmap.put(refId, (count == null) ? 1 : count + 1);
		}

		for (Map.Entry m : tmap.entrySet()) {
			if(Integer.valueOf(m.getValue().toString() )> 1){
				duplicateStageRefIds.add(m.getKey().toString());
			}
		}
		if( duplicateStageRefIds.size() > 0 ){
			throwPipelineValidationException(duplicateStageRefIds, "The refId property must be unique across stages. Duplicate id(s):");
		}

	}

	private void validateRequisiteStageRefIds(List<String> refIds, List<String> allRequisiteStageRefIds) {
		List<String> nonExistentStageRefIds =  new ArrayList<>();
		for (String refId : allRequisiteStageRefIds) {
         if(!refIds.contains(refId)){
			 nonExistentStageRefIds.add(refId);
		 }
		}
		if( nonExistentStageRefIds.size() > 0 ){
			throwPipelineValidationException(nonExistentStageRefIds, "The requisiteStageRefIds property must not reference non existent refId values. Invalid id(s): ");
		}
	}

	private void validateRefIdAndRequisiteStageRefIds(List stages) {
		List<String> selfReferStageRefIds =  new ArrayList<>();
		for(Object stageObject : stages){
			Map<String, Object> stage = (HashMap) stageObject;
			String refId = stage.get("refId").toString();
			List<String> requisiteStageRefIds = (ArrayList)stage.get("requisiteStageRefIds");
			if(requisiteStageRefIds.contains(refId)){
				selfReferStageRefIds.add(refId);
			}
		}
		if( selfReferStageRefIds.size() > 0 ) {
			throwPipelineValidationException(selfReferStageRefIds,"The requisiteStageRefIds property must not contain self-references. Self-referenced id(s):");
		}
	}
	private void throwPipelineValidationException(List<String> stageRefIds, String errorMsg){
		if(!stageRefIds.isEmpty() && stageRefIds.size() > 0) {
			logger.debug("errorMsg"+String.join(",", stageRefIds));
			throw new ValidationException("errorMsg" + String.join(",", stageRefIds), null);
		}
	}
}