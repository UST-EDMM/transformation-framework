package io.github.edmm.plugins.multi.ansible;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.edmm.core.DeploymentTechnology;
import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.core.execution.ExecutionContext;
import io.github.edmm.model.Property;
import io.github.edmm.model.component.Compute;
import io.github.edmm.plugins.DeploymentExecutor;

import com.google.gson.JsonObject;
import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnsibleExecutor extends DeploymentExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AnsibleExecutor.class);

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
        File directory = context.getDirectory();
        ProcessBuilder pb = new ProcessBuilder();
        pb.inheritIO();
        pb.directory(directory);

        //important for ip-address/hostname/ssh-port...
        Set<Compute> hosts = new HashSet<>();
        try {
            for (var component : context.getTransformation().getGroup().groupComponents) {
                Compute host = TopologyGraphHelper.resolveHostingComputeComponent(context.getTransformation().getTopologyGraph(), component)
                    .orElseThrow(() -> new IllegalArgumentException("can't find the hosting component"));
                hosts.add(host);
                var json = convertPropsToJson(component.getProperties());
                context.getFileAccess().write(component.getName() + "_requiredProps.json", json.toString());
            }
            for (var compute : hosts) {
                var json = convertPropsToJson(compute.getProperties());
                context.getFileAccess().write(compute.getName() + "_host.json", json.toString());

            }

            pb.command("ansible-playbook", "deployment.yml");

            Process apply = pb.start();
            apply.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.error(e.toString());
        }
    }

    @Override
    public void destroy() throws Exception {

    }
}




