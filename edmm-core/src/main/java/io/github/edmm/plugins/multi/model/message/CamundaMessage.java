package io.github.edmm.plugins.multi.model.message;

import java.util.HashMap;

public class CamundaMessage {

    private String messageName;
    private String tenantId;
    private HashMap<String, Object> processVariables;

    public CamundaMessage(String messageName, String tenantId, HashMap<String, Object> processVariables) {
        this.messageName = messageName;
        this.tenantId = tenantId;
        this.processVariables = processVariables;
    }

    public String getMessageName() {
        return messageName;
    }

    public void setMessageName(String messageName) {
        this.messageName = messageName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public HashMap<String, Object> getProcessVariables() {
        return processVariables;
    }

    public void setProcessVariables(HashMap<String, Object> processVariables) {
        this.processVariables = processVariables;
    }
}
