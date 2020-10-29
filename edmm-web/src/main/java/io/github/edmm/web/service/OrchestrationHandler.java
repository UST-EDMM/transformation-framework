package io.github.edmm.web.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.core.transformation.TransformationService;
import io.github.edmm.model.DeploymentModel;
import io.github.edmm.plugins.multi.MultiLifecycle;
import io.github.edmm.web.model.TransformationRequest;
import io.github.edmm.web.model.TriggerRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrchestrationHandler {

    private final TransformationService transformationService;

    @Value("${repository.path}")
    private String repositoryPath;

    public OrchestrationHandler(TransformationService transformationService) {
        this.transformationService = transformationService;
    }

    @Async
    public void doTransform(TransformationRequest model) {
        TransformationContext context;
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(model.getInput());
            String input = new String(decodedBytes);
            DeploymentModel deploymentModel = DeploymentModel.of(input);
            Path sourceDirectory = Paths.get(repositoryPath);
            Path newTargetDirectory = Paths.get(repositoryPath + "/multi");

            context = transformationService.createContext(deploymentModel, model.getTarget(), sourceDirectory.toFile(), newTargetDirectory.toFile());
            context.setId("123");

            MultiLifecycle multiLifecycle = new MultiLifecycle(context);
            multiLifecycle.transform();

        } catch (Exception e) {
            throw new IllegalStateException("Could not create transformation context", e);
        }

    }

    /**
     * Prepares the execution by retrieving the temporary saved transformation context
     * and passing the retrieved environment variables to the Multilifecycle
     * @param triggerRequest Valid RequestBody
     */
    @Async
    public void prepareExecution(TriggerRequest triggerRequest) {

        // Retrieves the saved transformation context
        MultiLifecycle multiLifecycle = new MultiLifecycle(triggerRequest.getMultiId());

        // If transformation context is available, then prepare execution in Multilifecycle
        if (multiLifecycle.isTransformationContextAvailable(triggerRequest.getMultiId())) {
            multiLifecycle.assignRuntimeVariablesToLifecycles(triggerRequest.getSourceComponent(),
                triggerRequest.getTargetComponent(), triggerRequest.getEnvironmentVariables());
        }

    }

}
