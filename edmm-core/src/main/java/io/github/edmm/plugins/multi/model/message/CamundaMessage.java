package io.github.edmm.plugins.multi.model.message;

import lombok.Data;

@Data
public class CamundaMessage {

    private String messageName;
    private String tenantId;
    private ProcessVariables processVariables;

}


