package io.github.edmm.plugins.multi.model;

import java.util.ArrayList;
import java.util.List;

public class ComponentResources {

    List<String> runtimePropsOutput;
    // maybe used later
    List<String> runtimeInputParams;

    private final String name;



    public ComponentResources(String name) {
        this.name = name;
        this.runtimeInputParams = new ArrayList<>();

    }

    public ComponentResources(String name, List<String> runtimeInputParams, List<String> runtimeOutput) {
        this.name = name;
        this.runtimeInputParams = runtimeInputParams;
        this.runtimePropsOutput = runtimeOutput;

    }

    public String getName() {
        return name;
    }

}
