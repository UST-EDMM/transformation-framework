package io.github.edmm.plugins.multi.kubernetes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.edmm.core.TopologyGraphHelper;
import io.github.edmm.core.plugin.PluginFileAccess;
import io.github.edmm.core.transformation.TransformationException;
import io.github.edmm.docker.Container;
import io.github.edmm.model.Property;
import io.github.edmm.model.component.RootComponent;
import io.github.edmm.model.relation.RootRelation;
import io.github.edmm.plugins.kubernetes.model.DeploymentResource;
import io.github.edmm.plugins.kubernetes.model.KubernetesResource;
import io.github.edmm.plugins.kubernetes.model.ServiceResource;

import lombok.var;
import org.apache.commons.io.FilenameUtils;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesResourceBuilder {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesResourceBuilder.class);

    private final List<KubernetesResource> resources = new ArrayList<>();

    private final Container stack;
    private final Graph<RootComponent, RootRelation> dependencyGraph;
    private final PluginFileAccess fileAccess;

    public KubernetesResourceBuilder(Container stack, Graph<RootComponent, RootRelation> dependencyGraph, PluginFileAccess fileAccess) {
        this.stack = stack;
        this.dependencyGraph = dependencyGraph;
        this.fileAccess = fileAccess;
    }

    public void populateResources() {
        resolveEnvVars();
        resources.add(new DeploymentResource(stack));
        if (stack.getPorts().size() > 0) {
            resources.add(new ServiceResource(stack));
        }
        resources.forEach(KubernetesResource::build);
        try {
            String targetDirectory = stack.getName();
            for (KubernetesResource resource : resources) {
                fileAccess.write(targetDirectory + "/" + resource.getName() + ".yaml", resource.toYaml());
            }
        } catch (Exception e) {
            logger.error("Failed to create Kubernetes resource files for stack '{}'", stack.getName(), e);
            throw new TransformationException(e);
        }
    }

    private boolean matchesBlacklist(Map.Entry<String, Property> prop) {
        String[] blacklist = {"*key_name*", "*public_key*", "hostname"};
        for (var blacklistVal : blacklist) {
            if (FilenameUtils.wildcardMatch(prop.getKey(), blacklistVal)) {
                return true;
            }
        }
        return false;
    }

    // build time stuff now
    private void resolveEnvVars() {
        var allProps = TopologyGraphHelper.resolveComponentStackProperties(dependencyGraph, stack.getRoot());
        for (var prop : allProps.entrySet()) {

            if (matchesBlacklist(prop)) {
                continue;
            }
            var envName = prop.getKey().toUpperCase();
            if (prop.getValue().isComputed() || prop.getValue().getValue() == null || prop.getValue().getValue().startsWith("$")) {
                stack.addEnvVarRuntime(envName);
            } else {
                stack.addEnvVar(envName, prop.getValue().getValue());
            }
        }
    }

}
