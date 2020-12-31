package io.github.edmm.web.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import io.github.edmm.core.transformation.TransformationContext;
import io.github.edmm.core.transformation.TransformationService;
import io.github.edmm.model.DeploymentModel;
import io.github.edmm.plugins.multi.MultiLifecycle;
import io.github.edmm.plugins.multi.model.ComponentProperties;
import io.github.edmm.web.model.DeployRequest;
import io.github.edmm.web.model.DeployResult;
import io.github.edmm.web.model.TransformationRequest;

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
            Path newTargetDirectory = Paths.get(repositoryPath + "/multi-" + deploymentModel.getMultiId());

            context = transformationService.createContext(deploymentModel, model.getTarget(), sourceDirectory.toFile(),
                newTargetDirectory.toFile());
            context.setId(deploymentModel.getMultiId());

            MultiLifecycle multiLifecycle = new MultiLifecycle(context);
            multiLifecycle.transform();

        } catch (Exception e) {
            throw new IllegalStateException("Could not create transformation context", e);
        }

    }

    /**
     * Prepares the execution by retrieving the temporary saved transformation context
     * and passing the retrieved environment variables to the Multilifecycle
     * @param deployRequest Valid RequestBody
     */
    public DeployResult prepareExecution(DeployRequest deployRequest) {

        // Retrieves the saved transformation context
        MultiLifecycle multiLifecycle = new MultiLifecycle(deployRequest.getModelId());

        // If transformation context is available, then prepare execution in Multilifecycle
        if (multiLifecycle.isTransformationContextAvailable(deployRequest.getModelId())) {

            if (deployRequest.getCorrelationId() == null) {
                UUID uuid = UUID.randomUUID();
                deployRequest.setCorrelationId(uuid);
            }

            List<ComponentProperties> properties =
                multiLifecycle.assignRuntimeVariablesToLifecycles(deployRequest.getComponents(),
                deployRequest.getInputs());

            return new DeployResult(deployRequest.getModelId(),
                deployRequest.getCorrelationId(),
                properties);
        }
        return null;
    }
}
