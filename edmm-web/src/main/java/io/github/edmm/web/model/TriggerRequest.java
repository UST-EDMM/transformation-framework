package io.github.edmm.web.model;

import java.util.Map;

import javax.validation.constraints.NotEmpty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TriggerRequest {

    @NotEmpty
    private String multiId;

    @NotEmpty
    private String sourceComponent;

    @NotEmpty
    private String targetComponent;

    private Map<String, String> environmentVariables;

}
