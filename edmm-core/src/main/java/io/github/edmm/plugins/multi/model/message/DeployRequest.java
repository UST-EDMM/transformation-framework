package io.github.edmm.plugins.multi.model.message;

import java.util.ArrayList;
import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import io.github.edmm.plugins.multi.model.ComponentProperties;

import lombok.Data;

@Data
public class DeployRequest {

    @NotEmpty
    private String modelId;

    private UUID correlationId;

    @NotEmpty
    private ArrayList<String> components;

    private ArrayList<ComponentProperties> inputs;

}
