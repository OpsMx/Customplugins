package com.opsmx.plugin.stage.custom;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.tasks.MonitorFront50Task;
import com.netflix.spinnaker.orca.front50.tasks.SavePipelineTask;
import com.netflix.spinnaker.orca.front50.tasks.SaveServiceAccountTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
@ComponentScan({"com.opsmx.plugin.stage.custom"})
@Component
public class SavePipelineStage implements StageDefinitionBuilder {

    public static final String SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";
    public static final String SHARED_SERVICE_ACCOUNT_SUFFIX = "@shared-managed-service-account";

    @Value("${tasks.use-managed-service-accounts:false}")
    boolean useManagedServiceAccounts;

    @Override
    public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
        if (useManagedServiceAccounts) {
            builder.withTask("updatePipelinePermissions", SaveServiceAccountTask.class);
        }

        builder
                .withTask("validatepipeline", PipelineRbacTask.class)
                .withTask("savePipeline", SavePipelineTask.class)
                .withTask("waitForPipelineSave", MonitorFront50Task.class);
    }
}