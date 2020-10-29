package io.github.edmm.plugins.multi.model;

import java.util.HashMap;
import java.util.Map;

public class ComponentProperties {

    private String component;
    private HashMap<String, String> properties;

    public ComponentProperties() {

    }

    public ComponentProperties(String component, HashMap<String, String> properties) {
        this.component = component;
        this.properties = properties;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(HashMap<String, String> properties) {
        this.properties = properties;
    }
}
