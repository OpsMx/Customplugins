package com.opsmx.plugin.stage.custom;

import javax.validation.constraints.NotNull;

import org.pf4j.Extension;
import org.springframework.stereotype.Component;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.applications.tasks.DeleteApplicationTask;

import groovy.transform.CompileStatic;

@Component
@Extension
@CompileStatic
public class DeleteApplicationStage implements StageDefinitionBuilder {

    @Override
    public void taskGraph(@NotNull StageExecution stage, @NotNull TaskNode.Builder builder) {

        builder.withTask("validateApplication", RBACValidationTask.class)
                .withTask("deleteTask", DeleteApplicationTask.class);
    }
}