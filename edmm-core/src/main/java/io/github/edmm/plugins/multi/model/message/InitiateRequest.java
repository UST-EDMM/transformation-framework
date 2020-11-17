package io.github.edmm.plugins.multi.model.message;

import java.util.HashMap;

public class InitiateRequest {

    private HashMap<String, Object> variables;

    public InitiateRequest() {

    }

    public InitiateRequest(HashMap<String, Object> processVariables) {
        this.variables = processVariables;
    }

    public HashMap<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(HashMap<String, Object> variables) {
        this.variables = variables;
    }
}
