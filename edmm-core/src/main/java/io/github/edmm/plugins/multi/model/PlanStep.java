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
}



