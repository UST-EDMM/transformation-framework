package io.github.edmm.plugins.multi.workflows;

import java.util.List;

import io.github.edmm.plugins.multi.Technology;
import io.github.edmm.plugins.multi.model.ComponentResources;

import lombok.Data;

@Data
public class BPMNStep {

    private int step;
    private Technology tech;
    private String component;
    private List<ComponentResources> components;
    private String participantEndpoint;
    private String participant;
    private List<String> input;
    private TaskType taskType;

    public enum TaskType {
        DEPLOY, SEND, RECEIVE
    }
}
