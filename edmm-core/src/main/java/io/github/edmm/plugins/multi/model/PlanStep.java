package io.github.edmm.plugins.multi.model;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import io.github.edmm.plugins.multi.Technology;

import org.yaml.snakeyaml.Yaml;

public class PlanStep {
    public Technology tech;
    // all components that will be deployed in this step
    public List<ComponentResources> components;
    private String participantEndpoint;
    private int step;
    private int processStep;

    public PlanStep(Technology tech) {
        components = new ArrayList<>();
        this.tech = tech;
    }

    public String toYaml() {
        Yaml yaml = new Yaml();
        StringWriter writer = new StringWriter();
        yaml.dump(this, writer);
        return writer.toString();
    }

    public List<ComponentResources> getComponents() {
        return components;
    }

    public int getStep() {
        return step;
    }

    public String getTech() {
        return tech.toString();
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getParticipantEndpoint() {
        return participantEndpoint;
    }

    public void setParticipantEndpoint(String participantEndpoint) {
        this.participantEndpoint = participantEndpoint;
    }

    public int getProcessStep() {
        return processStep;
    }

    public void setProcessStep(int processStep) {
        this.processStep = processStep;
    }
}



