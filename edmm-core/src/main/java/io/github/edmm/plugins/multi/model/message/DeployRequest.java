package io.github.edmm.plugins.multi.model.message;

import java.util.ArrayList;
import java.util.UUID;

import io.github.edmm.plugins.multi.model.ComponentProperties;

public class DeployRequest {

    private String modelId;

    private UUID correlationId;

    private ArrayList<String> components;

    private ArrayList<ComponentProperties> inputs;

    public DeployRequest() {

    }

    public DeployRequest(String modelId, UUID correlationId,
                         ArrayList<String> components,
                         ArrayList<ComponentProperties> inputs) {
        this.modelId = modelId;
        this.correlationId = correlationId;

        this.components = components;
        this.inputs = inputs;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public ArrayList<String> getComponents() {
        return components;
    }

    public void setComponents(ArrayList<String> components) {
        this.components = components;
    }

    public ArrayList<ComponentProperties> getInputs() {
        return inputs;
    }

    public void setInputs(ArrayList<ComponentProperties> inputs) {
        this.inputs = inputs;
    }
}
