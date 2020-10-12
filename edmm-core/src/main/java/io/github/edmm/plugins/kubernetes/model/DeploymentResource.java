package io.github.edmm.plugins.kubernetes.model;

import java.util.stream.Collectors;

import io.github.edmm.core.transformation.TransformationException;
import io.github.edmm.docker.Container;
import io.github.edmm.docker.PortMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeploymentResource implements KubernetesResource {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentResource.class);

    private Deployment deployment;
    private final Container stack;

    public DeploymentResource(Container stack) {
        this.stack = stack;
    }

    @Override
    public void build() {
        io.fabric8.kubernetes.api.model.Container container = new ContainerBuilder()
                .withImage(stack.getLabel() + ":latest").withName(stack.getLabel()).withImagePullPolicy("Never")
                .addAllToPorts(stack.getPorts().stream().map(PortMapping::toContainerPort).collect(Collectors.toList()))
                .addAllToEnv(stack.getEnvVars().entrySet().stream().map(e -> new EnvVar(e.getKey(), e.getValue(), null))
                        .collect(Collectors.toSet()))
                .addAllToEnv(stack.getEnvVarsRuntime().stream().map(name -> {
                    var source = new EnvVarSourceBuilder().withNewConfigMapKeyRef().withNewKey(name)
                            .withNewName(stack.getConfigMapName()).endConfigMapKeyRef().build();
                    return new EnvVarBuilder().withName(name).withValueFrom(source).build();
                }).collect(Collectors.toSet())).build();
        deployment = new DeploymentBuilder().withNewMetadata().withName(stack.getLabel()).withNamespace("default")
                .addToLabels("app", stack.getLabel()).endMetadata().withNewSpec().withReplicas(1).withNewSelector()
                .addToMatchLabels("app", stack.getLabel()).endSelector().withNewTemplate().withNewMetadata()
                .withName(stack.getLabel()).addToLabels("app", stack.getLabel()).endMetadata().withNewSpec()
                .addAllToContainers(Lists.newArrayList(container)).endSpec().endTemplate().endSpec().build();
    }

    @Override
    public String toYaml() {
        if (deployment == null) {
            throw new TransformationException("Resource not yet built, call build() first");
        }
        try {
            return SerializationUtils.dumpAsYaml(deployment);
        } catch (JsonProcessingException e) {
            logger.error("Failed to dump YAML", e);
            throw new TransformationException(e);
        }
    }

    @Override
    public String getName() {
        return stack.getDeploymentName();
    }
}
