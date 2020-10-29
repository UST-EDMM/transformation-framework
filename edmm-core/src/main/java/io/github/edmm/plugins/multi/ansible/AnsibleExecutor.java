package io.github.edmm.plugins.multi.ansible;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.edmm.core.DeploymentTechnology;
import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.core.execution.ExecutionContext;
import io.github.edmm.model.Property;
import io.github.edmm.model.component.Compute;
import io.github.edmm.plugins.DeploymentExecutor;
import io.github.edmm.plugins.multi.model.ComponentProperties;

import com.google.gson.JsonObject;
import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnsibleExecutor extends DeploymentExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AnsibleExecutor.class);
    private final List<ComponentProperties> properties = new ArrayList<>();


    public AnsibleExecutor(ExecutionContext context, DeploymentTechnology deploymentTechnology) {
        super(context, deploymentTechnology);
    }

    public JsonObject convertPropsToJson(Map<String, Property> resolvedComputedProps) {

        var json = new JsonObject();
        for (var prop : resolvedComputedProps.entrySet()) {
            json.addProperty(prop.getKey().toUpperCase(), prop.getValue().getValue());
        }
        return json;
    }

    @Override
    public void execute() throws Exception {
        deploy();
    }

    public void deploy() {
        File directory = context.getDirectory();
        ProcessBuilder pb = new ProcessBuilder();
        pb.inheritIO();
        pb.directory(directory);

        //important for ip-address/hostname/ssh-port...
        Set<Compute> hosts = new HashSet<>();
        try {
            for (var component : context.getTransformation().getGroup().groupComponents) {
                HashMap<String, String> outputVariables = new HashMap<>();
                Compute host = TopologyGraphHelper.resolveHostingComputeComponent(context.getTransformation().getTopologyGraph(), component)
                    .orElseThrow(() -> new IllegalArgumentException("can't find the hosting component"));
                hosts.add(host);

                component.getProperties().forEach((key, value) -> {
                    // Sets hostname, since it is not updated in the properties
                    if (key.equals("hostname")) {
                        value.setValue(host.getProperty("hostname").get().getValue());
                    }
                    outputVariables.put(key, value.getValue());
                });

                ComponentProperties propertiess = new ComponentProperties(
                    component.getName(),
                    outputVariables
                );
                properties.add(propertiess);
                var json = convertPropsToJson(component.getProperties());
                context.getFileAccess().write(component.getName() + "_requiredProps.json", json.toString());
            }
            for (var compute : hosts) {
                HashMap<String, String> outputVariables = new HashMap<>();
                var json = convertPropsToJson(compute.getProperties());
                compute.getProperties().forEach((key, value) -> {
                    outputVariables.put(key, value.getValue());
                });

                ComponentProperties propertiess = new ComponentProperties(
                    compute.getName(),
                    outputVariables
                );
                properties.add(propertiess);
                context.getFileAccess().write(compute.getName() + "_host.json", json.toString());

            }

            pb.command("ansible-playbook", "deployment.yml");

            Process apply = pb.start();
            apply.waitFor();

        } catch (IOException | InterruptedException e) {
            logger.error(e.toString());
        }
    }

    public List<ComponentProperties> executeWithOutputProperty() {
        deploy();
        return properties;
    }

    @Override
    public void destroy() throws Exception {
    }
}




