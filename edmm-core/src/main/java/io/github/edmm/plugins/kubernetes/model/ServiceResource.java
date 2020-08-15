package io.github.edmm.plugins.kubernetes.model;

import java.util.List;
import java.util.stream.Collectors;

import io.github.edmm.core.transformation.TransformationException;
import io.github.edmm.docker.Container;
import io.github.edmm.docker.PortMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServiceResource implements KubernetesResource {

    private static final Logger logger = LoggerFactory.getLogger(ServiceResource.class);

    private Service service;
    private final Container stack;

    public ServiceResource(Container stack) {
        this.stack = stack;
    }

    @Override
    public void build() {
        List<ServicePort> ports = stack.getPorts().stream().map(PortMapping::toServicePort)
                .collect(Collectors.toList());
        service = new ServiceBuilder().withNewMetadata().withName(stack.getServiceName())
                .addToLabels("app", stack.getServiceName()).withNamespace("default").endMetadata().withNewSpec()
                .addAllToPorts(ports).addToSelector("app", stack.getLabel()).withType("NodePort").endSpec().build();
    }

    @Override
    public String toYaml() {
        if (service == null) {
            throw new TransformationException("Resource not yet built, call build() first");
        }
        try {
            return SerializationUtils.dumpAsYaml(service);
        } catch (JsonProcessingException e) {
            logger.error("Failed to dump YAML", e);
            throw new TransformationException(e);
        }
    }

    @Override
    public String getName() {
        return stack.getServiceName();
    }
}
