package io.github.edmm.web.model;

import java.util.ArrayList;
import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import io.github.edmm.plugins.multi.model.ComponentProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeployRequest {

    @NotEmpty
    private String modelId;

    private UUID correlationId;

    @NotEmpty
    private ArrayList<String> components;

    private ArrayList<ComponentProperties> inputs;

}
