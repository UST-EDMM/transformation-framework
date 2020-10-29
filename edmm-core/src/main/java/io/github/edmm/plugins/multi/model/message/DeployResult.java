package io.github.edmm.plugins.multi.model.message;

import java.util.List;
import java.util.UUID;

import io.github.edmm.plugins.multi.model.ComponentProperties;

public class DeployResult {

    private String modelId;

    private UUID correlationId;

    private List<ComponentProperties> output;

    public DeployResult() {

    }

    public DeployResult(String modelId, UUID correlationId, List<ComponentProperties> output) {
        this.modelId = modelId;
        this.correlationId = correlationId;
        this.output = output;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public List<ComponentProperties> getOutput() {
        return output;
    }

    public void setOutput(List<ComponentProperties> output) {
        this.output = output;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }
}
